package com.planet.importexport.importapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import com.planet.importexport.authapi.support.BearerTokenTestSupport;
import com.planet.importexport.customerrecord.CustomerRecordDocument;
import com.planet.importexport.customerrecord.CustomerRecordRepository;
import com.planet.importexport.importjob.ImportJobRepository;
import com.planet.importexport.jobconfig.JobConfigurationEntry;
import com.planet.importexport.jobconfig.JobConfigurationRepository;
import com.planet.importexport.jobconfig.JobConfigurationValueType;
import com.planet.importexport.mongo.FlapdoodleMongoTestResource;
import com.planet.importexport.staging.StagingEntryRepository;

import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * End-to-end integration tests for {@code POST /api/v1/imports} ({@code B1})
 * and {@code GET /api/v1/imports/{jobId}} ({@code B10}), exercising the full
 * stack (real embedded MongoDB via {@link FlapdoodleMongoTestResource}, the
 * real managed executor, the real id-intersection gate) rather than mocking
 * collaborators — this is the resource that ties every Group B task together
 * end to end.
 *
 * <p>Because processing is asynchronous, tests poll the status endpoint until
 * the job reaches a terminal status rather than asserting immediately after
 * submission.</p>
 *
 * <p>{@code E1} (ADR-0006) made this resource {@code @Authenticated}; every
 * request below carries a bearer token obtained from the Dev-Services
 * Keycloak realm via {@link BearerTokenTestSupport} (see {@code AuthenticationIT}
 * for the dedicated authentication-scenario coverage — 401 without a token,
 * 200 with one — which is intentionally not re-asserted here to avoid
 * duplicating Group E's own test responsibility).</p>
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class ImportResourceIT {
    @Inject
    ImportJobRepository importJobRepository;

    @Inject
    StagingEntryRepository stagingEntryRepository;

    @Inject
    CustomerRecordRepository customerRecordRepository;

    @Inject
    JobConfigurationRepository jobConfigurationRepository;

    @Inject
    BearerTokenTestSupport bearerTokenTestSupport;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        importJobRepository.deleteAll();
        stagingEntryRepository.deleteAll();
        customerRecordRepository.deleteAll();

        /*
         * chunkSize is seeded once at application startup
         * (JobConfigurationSeedMigration); ensure a known, small value for
         * these tests without depending on the seeded default.
         */
        Optional<JobConfigurationEntry> existing =
                jobConfigurationRepository.findByKey("chunkSize");

        if (existing.isPresent()) {
            jobConfigurationRepository.update("chunkSize",
                                              "2",
                                              JobConfigurationValueType.INTEGER,
                                              "test override");
        } else {
            jobConfigurationRepository.insert(
                    new JobConfigurationEntry("chunkSize",
                                              "2",
                                              JobConfigurationValueType.INTEGER,
                                              "test override",
                                              Instant.now()));
        }
    }

    @AfterEach
    void cleanup() {
        importJobRepository.deleteAll();
        stagingEntryRepository.deleteAll();
        customerRecordRepository.deleteAll();
    }

    /**
     * Submitting a valid import request returns {@code 202 Accepted} with
     * only a {@code jobId} in the body, and no {@code status} or
     * {@code summary} fields yet since processing is asynchronous.
     */
    @Test
    void submit_returns202WithJobIdOnly()
            throws IOException {
        Path file =
                writeCsv("""
                         id,name,email,age,country
                         1,John Smith,john@example.com,35,Portugal
                         """);

        authenticatedRequest().contentType(ContentType.JSON)
                              .body("{\"filePath\":\"" + escape(file) + "\"}")
                              .when()
                              .post("/api/v1/imports")
                              .then()
                              .statusCode(202)
                              .body("jobId",
                                    Matchers.notNullValue())
                              .body("$",
                                    Matchers.not(Matchers.hasKey("status")))
                              .body("$",
                                    Matchers.not(Matchers.hasKey("summary")));
    }

    /**
     * Submitting an import request whose {@code filePath} does not point to
     * a readable file returns {@code 400 Bad Request}.
     */
    @Test
    void submit_returns400WhenFilePathNotReadable() {
        authenticatedRequest().contentType(ContentType.JSON)
                              .body("{\"filePath\":\"/does/not/exist.csv\"}")
                              .when()
                              .post("/api/v1/imports")
                              .then()
                              .statusCode(400);
    }

    /**
     * Submitting an import request with a blank {@code filePath} returns
     * {@code 400 Bad Request}.
     */
    @Test
    void submit_returns400WhenFilePathBlank() {
        authenticatedRequest().contentType(ContentType.JSON)
                              .body("{\"filePath\":\"\"}")
                              .when()
                              .post("/api/v1/imports")
                              .then()
                              .statusCode(400);
    }

    /**
     * Requesting the status of a job id that was never submitted returns
     * {@code 404 Not Found}.
     */
    @Test
    void status_returns404ForUnknownJob() {
        authenticatedRequest().when()
                              .get("/api/v1/imports/job-does-not-exist")
                              .then()
                              .statusCode(404);
    }

    /**
     * A file whose rows are all valid completes with a summary matching the
     * total/succeeded/failed row counts, no staging errors, and a current
     * customer record persisted for every id in the file.
     */
    @Test
    void fullySuccessfulImport_reportsSummaryAndEmptyStaging()
            throws Exception {
        Path file =
                writeCsv("""
                         id,name,email,age,country
                         1,John Smith,john@example.com,35,Portugal
                         2,Jane Doe,jane@example.com,28,Spain
                         3,Bob Smith,bob@example.com,42,France
                         """);

        String jobId = submitAndGetJobId(file);
        waitForTerminalStatus(jobId);

        authenticatedRequest().when()
                              .get("/api/v1/imports/" + jobId)
                              .then()
                              .statusCode(200)
                              .body("status",
                                    Matchers.equalTo("COMPLETED"))
                              .body("summary.totalRows",
                                    Matchers.equalTo(3))
                              .body("summary.succeeded",
                                    Matchers.equalTo(3))
                              .body("summary.failed",
                                    Matchers.equalTo(0))
                              .body("stagingErrors",
                                    Matchers.hasSize(0));

        Optional<CustomerRecordDocument> currentVersion1 =
                customerRecordRepository.findCurrentVersion("1");

        Optional<CustomerRecordDocument> currentVersion2 =
                customerRecordRepository.findCurrentVersion("2");

        Optional<CustomerRecordDocument> currentVersion3 =
                customerRecordRepository.findCurrentVersion("3");

        Assertions.assertThat(currentVersion1)
                  .isPresent();
        Assertions.assertThat(currentVersion2)
                  .isPresent();
        Assertions.assertThat(currentVersion3)
                  .isPresent();
    }

    /**
     * A file with reordered columns and an extra recognized field succeeds
     * and persists that extra field, while a separate row missing its
     * {@code age} value fails and is counted accordingly in the summary.
     */
    @Test
    void reorderedColumnsWithExtraFieldAndMissingAge_succeedsAndStagesRespectively()
            throws Exception {
        Path file =
                writeCsv("""
                         id,name,email,age,country,phone
                         1,John Smith,john@example.com,35,Portugal,+351910000000
                         4,Ana Costa,ana@example.com,,Portugal
                         """);

        String jobId = submitAndGetJobId(file);
        waitForTerminalStatus(jobId);

        authenticatedRequest().when()
                              .get("/api/v1/imports/" + jobId)
                              .then()
                              .statusCode(200)
                              .body("summary.succeeded",
                                    Matchers.equalTo(1))
                              .body("summary.failed",
                                    Matchers.equalTo(1));

        Optional<CustomerRecordDocument> currentVersion =
                customerRecordRepository.findCurrentVersion("1");

        Assertions.assertThat(currentVersion)
                  .hasValueSatisfying(doc ->
                                        Assertions.assertThat(doc.fields)
                                                  .containsEntry("phone",
                                                                 "+351910000000"));
    }

    /**
     * A row with a malformed email address fails validation and is reported
     * as a staging error whose description mentions "email" and whose row
     * data identifies the offending row by its id.
     */
    @Test
    void invalidEmail_isStagedWithDescriptionMentioningEmail()
            throws Exception {
        Path file =
                writeCsv("""
                         id,name,phone,email,age,country
                         5,Marco Rossi,+39000000000,marco@example,40,Italy
                         """);

        String jobId = submitAndGetJobId(file);
        waitForTerminalStatus(jobId);

        authenticatedRequest().when()
               .get("/api/v1/imports/" + jobId)
               .then()
               .statusCode(200)
               .body("summary.failed",
                     Matchers.equalTo(1))
               .body("stagingErrors[0].errorDescription",
                     Matchers.containsStringIgnoringCase("email"))
               .body("stagingErrors[0].rowData.id",
                    Matchers.equalTo("5"));
    }

    /**
     * A staging error returned over HTTP exposes all five required fields:
     * {@code jobId}, {@code rowId}, {@code rowData}, {@code errorDescription},
     * and {@code processedAt}.
     */
    @Test
    void stagingEntry_exposesAllFiveRequiredFieldsViaHttp()
            throws Exception {
        Path file =
                writeCsv("""
                         id,name,phone,email,age,country
                         7,Staged Customer,+35190000000,not-an-email,45,Portugal
                         """);

        String jobId = submitAndGetJobId(file);
        waitForTerminalStatus(jobId);

        authenticatedRequest().when()
                              .get("/api/v1/imports/" + jobId)
                              .then()
                              .statusCode(200)
                              .body("summary.failed",
                                    Matchers.equalTo(1))
                              .body("stagingErrors[0].jobId",
                                    Matchers.equalTo(jobId))
                              .body("stagingErrors[0].rowId",
                                    Matchers.equalTo(1))
                              .body("stagingErrors[0].rowData.id",
                                    Matchers.equalTo("7"))
                              .body("stagingErrors[0].errorDescription",
                                    Matchers.notNullValue())
                              .body("stagingErrors[0].processedAt",
                                    Matchers.notNullValue());
    }

    /**
     * A row under a header that declares an unrecognized column fails
     * validation and is staged with an error description that names the
     * unknown column.
     */
    @Test
    void unknownHeaderColumn_isStagedWithDescriptionMentioningColumnName()
            throws Exception {
        Path file =
                writeCsv("""
                         id,name,email,age,country,loyalty_tier
                         6,New Customer,new@example.com,30,Germany,Gold
                         """);

        String jobId = submitAndGetJobId(file);
        waitForTerminalStatus(jobId);

        authenticatedRequest().when()
                              .get("/api/v1/imports/" + jobId)
                              .then()
                              .statusCode(200)
                              .body("summary.failed",
                                    Matchers.equalTo(1))
                              .body("stagingErrors[0].errorDescription",
                                    Matchers.containsString("loyalty_tier"));
    }

    /**
     * Re-importing an id already on file, in a separate job submitted after
     * the first completes, creates a new version that merges the new field
     * values with the fields inherited from the prior version.
     */
    @Test
    void reimportingSameId_createsNewMergedVersionInArrivalOrder()
            throws Exception {
        Path fileA = writeCsv("""
                              id,name,email,age,country
                              1,John Smith,john@example.com,35,Portugal
                              """);

        String jobA = submitAndGetJobId(fileA);

        waitForTerminalStatus(jobA);

        Path fileB = writeCsv("id,country\n1,Spain\n");
        String jobB = submitAndGetJobId(fileB);

        waitForTerminalStatus(jobB);

        var currentVersion =
                customerRecordRepository.findCurrentVersion("1")
                                        .orElseThrow();

        Assertions.assertThat(currentVersion.version)
                  .isEqualTo(2);

        Assertions.assertThat(currentVersion.fields)
                  .containsEntry("country", "Spain")
                  .containsEntry("name", "John Smith");
    }

    /**
     * A 5-row file processed with a configured chunk size of 2 (three
     * chunks) completes with every row succeeding and a current customer
     * record persisted for each of the five ids.
     */
    @Test
    void fiveRowFileWithChunkSizeTwo_processesAllThreeChunksToCompletion()
            throws Exception {
        Path file =
                writeCsv("""
                         id,name,email,age,country
                         1,Alice One,alice1@example.com,30,Portugal
                         2,Alice Two,alice2@example.com,31,Spain
                         3,Alice Three,alice3@example.com,32,France
                         4,Alice Four,alice4@example.com,33,Italy
                         5,Alice Five,alice5@example.com,34,Germany
                         """);

        String jobId = submitAndGetJobId(file);
        waitForTerminalStatus(jobId);

        authenticatedRequest().when()
                              .get("/api/v1/imports/" + jobId)
                              .then()
                              .statusCode(200)
                              .body("status",
                                    Matchers.equalTo("COMPLETED"))
                              .body("summary.totalRows",
                                    Matchers.equalTo(5))
                              .body("summary.succeeded",
                                    Matchers.equalTo(5))
                              .body("summary.failed",
                                    Matchers.equalTo(0));

        for (int recordId = 1; recordId <= 5; recordId++) {
            Optional<CustomerRecordDocument> currentVersion =
                    customerRecordRepository.findCurrentVersion(String.valueOf(recordId));
            Assertions.assertThat(currentVersion)
                      .isPresent();
        }
    }

    /**
     * A file with several hundred valid rows is accepted and completes with
     * a summary whose total/succeeded row counts match the generated row
     * count, proving there is no undocumented size ceiling on the endpoint.
     */
    @Test
    void largeFileWithSeveralHundredRows_isAcceptedAndCompletesWithMatchingTotalRows()
            throws Exception {
        int rowCount = 300;
        Path file = writeCsv(generateValidRows(rowCount));

        String jobId = submitAndGetJobId(file);
        waitForTerminalStatus(jobId);

        authenticatedRequest().when()
                              .get("/api/v1/imports/" + jobId)
                              .then()
                              .statusCode(200)
                              .body("status",
                                    Matchers.equalTo("COMPLETED"))
                              .body("summary.totalRows",
                                    Matchers.equalTo(rowCount))
                              .body("summary.succeeded",
                                    Matchers.equalTo(rowCount))
                              .body("summary.failed",
                                    Matchers.equalTo(0));
    }

    /**
     * Two jobs submitted concurrently whose files reference entirely
     * disjoint ids both complete successfully, each with its own current
     * customer record persisted.
     */
    @Test
    void disjointIdJobsBothCompleteSuccessfully()
            throws Exception {
        Path fileA =
                writeCsv("""
                         id,name,email,age,country
                         1,Alice,alice@example.com,30,Portugal
                         """);

        Path fileB =
                writeCsv("""
                         id,name,email,age,country
                         2,Bob,bob@example.com,40,Spain
                         """);

        String jobA = submitAndGetJobId(fileA);
        String jobB = submitAndGetJobId(fileB);

        waitForTerminalStatus(jobA);
        waitForTerminalStatus(jobB);

        Optional<CustomerRecordDocument> currentVersion1 =
                customerRecordRepository.findCurrentVersion("1");

        Optional<CustomerRecordDocument> currentVersion2 =
                customerRecordRepository.findCurrentVersion("2");

        Assertions.assertThat(currentVersion1)
                  .isPresent();

        Assertions.assertThat(currentVersion2)
                  .isPresent();
    }

    /**
     * Submits {@code file} for import and extracts the generated
     * {@code jobId} from the {@code 202 Accepted} response.
     *
     * @param file the CSV file to submit
     *
     * @return the newly generated {@code jobId}
     */
    private String submitAndGetJobId(Path file) {
        return authenticatedRequest().contentType(ContentType.JSON)
                                     .body("{\"filePath\":\"" + escape(file) + "\"}")
                                     .when()
                                     .post("/api/v1/imports")
                                     .then()
                                     .statusCode(202)
                                     .extract()
                                     .path("jobId");
    }

    /**
     * Polls the job status endpoint until {@code jobId} reaches a terminal
     * status ({@code COMPLETED} or {@code FAILED}), failing the test if it
     * does not within 30 seconds.
     *
     * @param jobId the job to poll
     *
     * @throws InterruptedException if the polling thread is interrupted
     */
    private void waitForTerminalStatus(String jobId)
            throws InterruptedException {
        /*
         * 30s accommodates the large-file test (several hundred rows chunked at chunkSize=2,
         * i.e. ~150 chunks) as well as the small fixed-row-count tests, which complete well
         * within this budget in the common case.
         */
        long deadline = System.currentTimeMillis() + 30_000;

        while (System.currentTimeMillis() < deadline) {
            String status = authenticatedRequest().when()
                                                  .get("/api/v1/imports/" + jobId)
                                                  .then()
                                                  .extract()
                                                  .path("status");

            if ("COMPLETED".equals(status)
            ||  "FAILED".equals(status)) {
                return;
            }

            Thread.sleep(50);
        }

        throw new AssertionError("Job " + jobId + " did not reach a terminal status in time");
    }

    /**
     * Writes {@code content} to a new temporary CSV file.
     *
     * @param content the file content to write
     *
     * @return the path of the newly written file
     *
     * @throws IOException if the file cannot be written
     */
    private Path writeCsv(String content)
            throws IOException {
        Path file = Files.createTempFile(tempDir, "import", ".csv");
        Files.writeString(file, content);

        return file;
    }

    /**
     * Generates a CSV document (header plus {@code rowCount} data rows) with
     * distinct, entirely valid values per row, used to prove there is no
     * undocumented size/row-count rejection ceiling on the import endpoint.
     *
     * @param rowCount the number of valid data rows to generate, each with a
     *                 unique {@code id}
     *
     * @return the full CSV content, header included
     */
    private String generateValidRows(int rowCount) {
        String header = "id,name,email,age,country";

        String rows =
                IntStream.rangeClosed(1, rowCount)
                         .mapToObj(rowNumber -> "%d,Customer %d,customer%d@example.com,%d,Portugal"
                                                .formatted(rowNumber,
                                                           rowNumber,
                                                           rowNumber,
                                                           20 + (rowNumber % 50)))
                         .collect(Collectors.joining("\n"));

        return header + "\n" + rows + "\n";
    }

    /**
     * Escapes backslashes in {@code path} so it can be embedded in a JSON
     * string literal.
     *
     * @param path the path to escape
     *
     * @return the path's string form with backslashes escaped
     */
    private String escape(Path path) {
        return path.toString()
                   .replace("\\", "\\\\");
    }

    /**
     * Builds a pre-authorized request specification for this class's HTTP
     * calls, so token acquisition/header wiring is not repeated at every
     * call site.
     *
     * @return a REST Assured request specification pre-authorized with a
     *         fresh bearer token from the Dev-Services Keycloak realm (see
     *         {@link BearerTokenTestSupport}), so every HTTP call in this
     *         class reaches the now-{@code @Authenticated} resource
     *         ({@code E1}) without repeating the token-acquisition/header
     *         wiring at every call site
     */
    private RequestSpecification authenticatedRequest() {
        return given().auth()
                      .oauth2(bearerTokenTestSupport.obtainAccessToken());
    }
}
