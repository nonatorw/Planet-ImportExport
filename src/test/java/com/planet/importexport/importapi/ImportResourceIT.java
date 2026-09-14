package com.planet.importexport.importapi;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.planet.importexport.authapi.support.BearerTokenTestSupport;
import com.planet.importexport.customerrecord.CustomerRecordRepository;
import com.planet.importexport.importjob.ImportJobRepository;
import com.planet.importexport.jobconfig.JobConfigurationEntry;
import com.planet.importexport.jobconfig.JobConfigurationRepository;
import com.planet.importexport.jobconfig.JobConfigurationValueType;
import com.planet.importexport.mongo.FlapdoodleMongoTestResource;
import com.planet.importexport.staging.StagingEntryRepository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

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
    @Inject ImportJobRepository importJobRepository;
    @Inject StagingEntryRepository stagingEntryRepository;
    @Inject CustomerRecordRepository customerRecordRepository;
    @Inject JobConfigurationRepository jobConfigurationRepository;
    @Inject BearerTokenTestSupport bearerTokenTestSupport;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        importJobRepository.deleteAll();
        stagingEntryRepository.deleteAll();
        customerRecordRepository.deleteAll();
        // chunkSize is seeded once at application startup (JobConfigurationSeedMigration); ensure a
        // known, small value for these tests without depending on the seeded default.
        Optional<JobConfigurationEntry> existing = jobConfigurationRepository.findByKey("chunkSize");
        if (existing.isPresent()) {
            jobConfigurationRepository.update("chunkSize",
                                              "2",
                                              JobConfigurationValueType.INTEGER,
                                              "test override");
        } else {
            jobConfigurationRepository.insert(
                    new JobConfigurationEntry(
                            "chunkSize",
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

    @Test
    void submit_returns202WithJobIdOnly() throws IOException {
        Path file = writeCsv("id,name,email,age,country\n1,John Smith,john@example.com,35,Portugal\n");

        authenticatedRequest().contentType(ContentType.JSON)
               .body("{\"filePath\":\"" + escape(file) + "\"}")
               .when()
               .post("/api/v1/imports")
               .then()
               .statusCode(202)
               .body("jobId", notNullValue())
               .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("status")))
               .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("summary")));
    }

    @Test
    void submit_returns400WhenFilePathNotReadable() {
        authenticatedRequest().contentType(ContentType.JSON)
               .body("{\"filePath\":\"/does/not/exist.csv\"}")
               .when()
               .post("/api/v1/imports")
               .then()
               .statusCode(400);
    }

    @Test
    void submit_returns400WhenFilePathBlank() {
        authenticatedRequest().contentType(ContentType.JSON)
               .body("{\"filePath\":\"\"}")
               .when()
               .post("/api/v1/imports")
               .then()
               .statusCode(400);
    }

    @Test
    void status_returns404ForUnknownJob() {
        authenticatedRequest().when()
               .get("/api/v1/imports/job-does-not-exist")
               .then()
               .statusCode(404);
    }

    @Test
    void fullySuccessfulImport_reportsSummaryAndEmptyStaging() throws Exception {
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
               .body("status", equalTo("COMPLETED"))
               .body("summary.totalRows", equalTo(3))
               .body("summary.succeeded", equalTo(3))
               .body("summary.failed", equalTo(0))
               .body("stagingErrors", org.hamcrest.Matchers.hasSize(0));

        assertThat(customerRecordRepository.findCurrentVersion("1")).isPresent();
        assertThat(customerRecordRepository.findCurrentVersion("2")).isPresent();
        assertThat(customerRecordRepository.findCurrentVersion("3")).isPresent();
    }

    @Test
    void reorderedColumnsWithExtraFieldAndMissingAge_succeedsAndStagesRespectively() throws Exception {
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
               .body("summary.succeeded", equalTo(1))
               .body("summary.failed", equalTo(1));

        assertThat(customerRecordRepository.findCurrentVersion("1"))
                .isPresent()
                .get()
                .satisfies(doc -> assertThat(doc.fields)
                                          .containsEntry("phone",
                                                         "+351910000000"));
    }

    @Test
    void invalidEmail_isStagedWithDescriptionMentioningEmail() throws Exception {
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
               .body("summary.failed", equalTo(1))
               .body("stagingErrors[0].errorDescription", org.hamcrest.Matchers.containsStringIgnoringCase("email"))
               .body("stagingErrors[0].rowData.id", equalTo("5"));
    }

    @Test
    void unknownHeaderColumn_isStagedWithDescriptionMentioningColumnName() throws Exception {
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
               .body("summary.failed", equalTo(1))
               .body("stagingErrors[0].errorDescription", org.hamcrest.Matchers.containsString("loyalty_tier"));
    }

    @Test
    void reimportingSameId_createsNewMergedVersionInArrivalOrder() throws Exception {
        Path fileA = writeCsv("id,name,email,age,country\n1,John Smith,john@example.com,35,Portugal\n");
        String jobA = submitAndGetJobId(fileA);

        waitForTerminalStatus(jobA);

        Path fileB = writeCsv("id,country\n1,Spain\n");
        String jobB = submitAndGetJobId(fileB);

        waitForTerminalStatus(jobB);

        var currentVersion = customerRecordRepository.findCurrentVersion("1")
                                                     .orElseThrow();

        assertThat(currentVersion.version).isEqualTo(2);

        assertThat(currentVersion.fields).containsEntry("country", "Spain")
                                         .containsEntry("name", "John Smith");
    }

    @Test
    void disjointIdJobsBothCompleteSuccessfully() throws Exception {
        Path fileA = writeCsv("id,name,email,age,country\n1,Alice,alice@example.com,30,Portugal\n");
        Path fileB = writeCsv("id,name,email,age,country\n2,Bob,bob@example.com,40,Spain\n");

        String jobA = submitAndGetJobId(fileA);
        String jobB = submitAndGetJobId(fileB);

        waitForTerminalStatus(jobA);
        waitForTerminalStatus(jobB);

        assertThat(customerRecordRepository.findCurrentVersion("1")).isPresent();
        assertThat(customerRecordRepository.findCurrentVersion("2")).isPresent();
    }

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

    private void waitForTerminalStatus(String jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;

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

    private Path writeCsv(String content) throws IOException {
        Path file = Files.createTempFile(tempDir, "import", ".csv");
        Files.writeString(file, content);

        return file;
    }

    private String escape(Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    /**
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
