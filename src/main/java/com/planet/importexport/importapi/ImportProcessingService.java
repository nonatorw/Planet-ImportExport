package com.planet.importexport.importapi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;

import com.planet.importexport.customerrecord.CustomerRecordRepository;
import com.planet.importexport.importapi.exception.FileNotReadableException;
import com.planet.importexport.importapi.model.CsvRow;
import com.planet.importexport.importapi.model.RowOutcome;
import com.planet.importexport.importapi.support.CsvFileReader;
import com.planet.importexport.importapi.support.CsvHeaderReader;
import com.planet.importexport.importapi.support.ImportExecutor;
import com.planet.importexport.importapi.support.JobIntersectionGate;
import com.planet.importexport.importapi.validator.ImportRowValidator;
import com.planet.importexport.importjob.ImportJobDocument;
import com.planet.importexport.importjob.ImportJobIdGenerator;
import com.planet.importexport.importjob.ImportJobRepository;
import com.planet.importexport.importjob.ImportJobSummary;
import com.planet.importexport.jobconfig.JobConfigurationRepository;
import com.planet.importexport.staging.StagingEntry;
import com.planet.importexport.staging.StagingEntryRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Orchestrates the whole import processing flow (design.md section 3):
 * validates the submitted file path, persists the {@code PENDING} job,
 * computes {@code idsInFile}, submits the job to the managed executor
 * (ADR-0002), and — inside that executor task — acquires the ADR-0003
 * id-intersection gate ({@code B2}), processes the file in chunks read from
 * the current {@code chunkSize} configuration ({@code B3}), validates and
 * stages/persists each row ({@code B4}-{@code B8}), and finalizes the job's
 * status and summary ({@code B9}).
 */
@ApplicationScoped
public class ImportProcessingService {
    private static final Logger LOG =
            Logger.getLogger(ImportProcessingService.class);

    private static final String CHUNK_SIZE_KEY = "chunkSize";

    private final Clock clock;
    private final CustomerRecordRepository customerRecordRepository;
    private final ExecutorService importExecutor;
    private final ImportJobIdGenerator jobIdGenerator;
    private final ImportJobRepository importJobRepository;
    private final JobConfigurationRepository jobConfigurationRepository;
    private final JobIntersectionGate gate;
    private final StagingEntryRepository stagingEntryRepository;

    /**
     * CDI-injected constructor: delegates to the package-visible constructor
     * with a real {@link ImportJobIdGenerator} and the system UTC clock.
     *
     * @param importJobRepository        persists and queries import jobs
     * @param stagingEntryRepository     persists rows that fail validation
     * @param customerRecordRepository   persists successful row versions
     * @param jobConfigurationRepository supplies the current {@code chunkSize}
     * @param gate                       the ADR-0003 id-intersection gate
     * @param importExecutor             the ADR-0002 bounded executor
     *                                   qualified by {@link ImportExecutor}
     */
    @Inject
    public ImportProcessingService(ImportJobRepository importJobRepository,
                                   StagingEntryRepository stagingEntryRepository,
                                   CustomerRecordRepository customerRecordRepository,
                                   JobConfigurationRepository jobConfigurationRepository,
                                   JobIntersectionGate gate,
                                   @ImportExecutor ExecutorService importExecutor) {
        this(importJobRepository,
             stagingEntryRepository,
             customerRecordRepository,
             jobConfigurationRepository,
             gate,
             importExecutor,
             new ImportJobIdGenerator(),
             Clock.systemUTC());
    }

    /**
     * Package-visible constructor for tests to inject a fixed {@link Clock}
     * and id generator.
     *
     * @param importJobRepository        persists and queries import jobs
     * @param stagingEntryRepository     persists rows that fail validation
     * @param customerRecordRepository   persists successful row versions
     * @param jobConfigurationRepository supplies the current {@code chunkSize}
     * @param gate                       the ADR-0003 id-intersection gate
     * @param importExecutor             the ADR-0002 bounded executor
     * @param jobIdGenerator             generates new job identifiers
     * @param clock                      the clock used for every recorded
     *                                   timestamp
     */
    ImportProcessingService(ImportJobRepository importJobRepository,
                            StagingEntryRepository stagingEntryRepository,
                            CustomerRecordRepository customerRecordRepository,
                            JobConfigurationRepository jobConfigurationRepository,
                            JobIntersectionGate gate,
                            ExecutorService importExecutor,
                            ImportJobIdGenerator jobIdGenerator,
                            Clock clock) {
        this.importJobRepository = importJobRepository;
        this.stagingEntryRepository = stagingEntryRepository;
        this.customerRecordRepository = customerRecordRepository;
        this.jobConfigurationRepository = jobConfigurationRepository;
        this.gate = gate;
        this.importExecutor = importExecutor;
        this.jobIdGenerator = jobIdGenerator;
        this.clock = clock;
    }

    /**
     * {@code B1}: validates {@code filePath} is readable, persists the
     * {@code PENDING} job with its up-front {@code idsInFile}, registers its
     * arrival with the ADR-0003 gate (synchronously, on the calling/request
     * thread, so arrival order matches submission order exactly — see
     * {@link JobIntersectionGate}), submits processing to the executor, and
     * returns the new {@code jobId} immediately without waiting for processing.
     *
     * @param filePath the path of the CSV file to import, as submitted by the
     *                 client
     * @return the newly generated {@code jobId}
     * @throws FileNotReadableException if {@code filePath} does not reference
     *                                  a readable file
     */
    public String submit(String filePath) {
        Path path = Path.of(filePath);

        if (!Files.isReadable(path)
        ||  !Files.isRegularFile(path)) {
            throw new FileNotReadableException(filePath);
        }

        String jobId = jobIdGenerator.generate();
        Instant submittedAt = Instant.now(clock);
        List<String> idsInFile = CsvFileReader.readIdColumn(path);

        ImportJobDocument job = new ImportJobDocument(jobId,
                                                      filePath,
                                                      submittedAt,
                                                      idsInFile);
        importJobRepository.persist(job);

        // Arrival is registered here, on the request thread, before the
        // executor task is even submitted — this is what makes arrival order
        // match submission order (ADR-0003), not whatever order the executor
        // happens to schedule worker threads in.
        gate.arrive(jobId, Set.copyOf(idsInFile));

        importExecutor.submit(() -> processJob(jobId, path));

        return jobId;
    }

    /**
     * The executor task body (ADR-0002): waits its turn on the ADR-0003 gate,
     * then runs {@code B3}-{@code B9}. Any unexpected failure (e.g. the file
     * becomes unreadable mid-processing, an I/O error) marks the job
     * {@code FAILED} with whatever partial summary was accumulated, rather
     * than leaving it stuck {@code RUNNING} forever.
     *
     * @param jobId the job to process
     * @param path  the already-validated source file path
     */
    private void processJob(String jobId, Path path) {
        try {
            gate.awaitTurn(jobId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warnf("Interrupted while waiting for the id-intersection gate for job %s",
                      jobId);

            return;
        }

        try {
            runChunkedProcessing(jobId, path);

        } finally {
            gate.release(jobId);
        }
    }

    /**
     * Runs {@code B3}-{@code B9} for one job: reads the current
     * {@code chunkSize} and unknown-column set, streams the file in chunks,
     * and finalizes the job as {@code COMPLETED} or {@code FAILED}.
     *
     * @param jobId the job being processed
     * @param path  the already-validated source file path
     */
    private void runChunkedProcessing(String jobId,
                                      Path path) {
        Instant startedAt = Instant.now(clock);
        importJobRepository.markRunning(jobId, startedAt);

        AtomicInteger totalRows = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        try {
            // B3: chunkSize is re-read from job configuration at the start of
            // every job — never cached across jobs — so a runtime
            // configuration change takes effect on the next job (design.md
            // section 3, step 4).
            int chunkSize =
                    jobConfigurationRepository.getIntValue(CHUNK_SIZE_KEY);
            Set<String> unknownColumns =
                    CsvHeaderReader.unknownColumns(
                            CsvHeaderReader.readHeader(path));

            CsvFileReader.readInChunks(path,
                                       chunkSize,
                                       chunk -> processChunk(jobId,
                                                             chunk,
                                                             unknownColumns,
                                                             totalRows,
                                                             succeeded,
                                                             failed));

            ImportJobSummary summary =
                    new ImportJobSummary(totalRows.get(),
                                         succeeded.get(),
                                         failed.get());

            importJobRepository.markCompleted(jobId,
                                              Instant.now(clock),
                                              summary);

        } catch (RuntimeException e) {
            ImportJobSummary partialSummary =
                    new ImportJobSummary(totalRows.get(),
                                         succeeded.get(),
                                         failed.get());

            importJobRepository.markFailed(jobId,
                                           Instant.now(clock),
                                           partialSummary);

            LOG.errorf(e,
                       "Import job %s failed while processing file %s",
                       jobId,
                       path);
        }
    }

    /**
     * {@code B4}-{@code B8}: validates every row in one chunk, staging or
     * persisting each.
     *
     * @param jobId          the job the chunk belongs to
     * @param chunk          the rows to validate and persist/stage
     * @param unknownColumns header columns (if any) outside the recognized
     *                       schema, computed once per file
     * @param totalRows      running total of rows processed so far; updated
     *                       in place
     * @param succeeded      running count of successfully persisted rows;
     *                       updated in place
     * @param failed         running count of staged rows; updated in place
     */
    private void processChunk(String jobId,
                              List<CsvRow> chunk,
                              Set<String> unknownColumns,
                              AtomicInteger totalRows,
                              AtomicInteger succeeded,
                              AtomicInteger failed) {
        for (CsvRow row : chunk) {
            totalRows.incrementAndGet();

            RowOutcome outcome =
                    ImportRowValidator.validate(row, unknownColumns);

            switch (outcome) {
                case RowOutcome.Success success -> {
                    customerRecordRepository.insertNextVersion(success.recordId(),
                                                               success.recognizedFields(),
                                                               jobId);
                    succeeded.incrementAndGet();
                }

                case RowOutcome.Failure failure -> {
                    stageRow(jobId,
                             row,
                             failure.errorDescription());
                    failed.incrementAndGet();
                }
            }
        }
    }

    /**
     * Persists one failed row as a {@link StagingEntry}.
     *
     * @param jobId            the job the row belongs to
     * @param row              the row that failed validation
     * @param errorDescription free text explaining why the row was staged
     */
    private void stageRow(String jobId,
                          CsvRow row,
                          String errorDescription) {
        StagingEntry entry = new StagingEntry();
        entry.jobId = jobId;
        entry.rowId = row.rowId();
        entry.rowData = row.asRowData();
        entry.errorDescription = errorDescription;
        entry.processedAt = Instant.now(clock);
        stagingEntryRepository.persist(entry);
    }
}
