package com.planet.importexport.jobconfigapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.planet.importexport.authapi.support.BearerTokenTestSupport;
import com.planet.importexport.jobconfig.JobConfigurationEntry;
import com.planet.importexport.jobconfig.JobConfigurationRepository;
import com.planet.importexport.jobconfig.JobConfigurationValueType;
import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

/**
 * End-to-end REST Assured coverage of the 5 {@code /api/v1/job-configurations}
 * endpoints (design.md section 2; spec: job-configuration; tasks D1-D2),
 * exercised against the real embedded Flapdoodle MongoDB instance (ADR-0001)
 * rather than a mocked {@link JobConfigurationRepository} — this resource is a
 * thin CRUD delegator with no business logic worth isolating behind a mock,
 * so a full HTTP-to-Mongo round trip is the more meaningful test per
 * {@code @422-frameworks-quarkus-testing-integration-tests}.
 *
 * <p>{@code E1} (ADR-0006) made this resource {@code @Authenticated}; every
 * request below carries a bearer token obtained from the Dev-Services
 * Keycloak realm via {@link BearerTokenTestSupport} — see
 * {@code AuthenticationIT} for the dedicated 401/200 authentication-scenario
 * coverage.</p>
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class JobConfigurationResourceIT {

    private static final String BASE_PATH = "/api/v1/job-configurations";

    @Inject
    JobConfigurationRepository repository;

    @Inject
    BearerTokenTestSupport bearerTokenTestSupport;

    // Same rationale as JobConfigurationRepositoryIT: the shared Quarkus test
    // context already ran JobConfigurationSeedMigration's
    // @Observes StartupEvent (seeding "chunkSize") before any test method
    // executes, so every test must start from a clean, known collection state.
    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void list_returnsAllEntries() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "batch size",
                                          Instant.now()));
        repository.insert(
                new JobConfigurationEntry("retryEnabled",
                                          "true",
                                          JobConfigurationValueType.BOOLEAN,
                                          "retry flag",
                                          Instant.now()));

        authenticatedRequest().when()
               .get(BASE_PATH)
               .then()
               .statusCode(200)
               .body("$", hasSize(2));
    }

    @Test
    void list_returnsEmptyArray_whenNoEntries() {
        authenticatedRequest().when()
               .get(BASE_PATH)
               .then()
               .statusCode(200)
               .body("$", hasSize(0));
    }

    @Test
    void getByKey_returns200WithEntry_whenPresent() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "batch size",
                                          Instant.now()));

        authenticatedRequest().when()
               .get(BASE_PATH + "/chunkSize")
               .then()
               .statusCode(200)
               .body("key", equalTo("chunkSize"))
               .body("value", equalTo("500"))
               .body("valueType", equalTo("INTEGER"))
               .body("description", equalTo("batch size"))
               .body("updatedAt", notNullValue());
    }

    @Test
    void getByKey_returns404_whenAbsent() {
        authenticatedRequest().when()
               .get(BASE_PATH + "/doesNotExist")
               .then()
               .statusCode(404);
    }

    @Test
    void create_returns201WithLocation_andPersistsEntry() {
        String body = """
                      {"key":"chunkSize","value":"500","valueType":"INTEGER","description":"batch size"}
                      """;

        authenticatedRequest().contentType(ContentType.JSON)
               .body(body)
               .when()
               .post(BASE_PATH)
               .then()
               .statusCode(201)
               .header("Location", org.hamcrest.Matchers.containsString(BASE_PATH + "/chunkSize"))
               .body("key", equalTo("chunkSize"))
               .body("value", equalTo("500"));

        assertPersisted("chunkSize",
                        "500",
                        JobConfigurationValueType.INTEGER,
                        "batch size");
    }

    @Test
    void create_returns400_whenValueInconsistentWithDeclaredType() {
        String body = """
                      {"key":"chunkSize","value":"not-a-number","valueType":"INTEGER","description":"batch size"}
                      """;

        authenticatedRequest().contentType(ContentType.JSON)
               .body(body)
               .when()
               .post(BASE_PATH)
               .then()
               .statusCode(400);
    }

    @Test
    void create_returns400_whenRequiredFieldMissing() {
        String body = """
                      {"value":"500","valueType":"INTEGER","description":"batch size"}
                      """;

        authenticatedRequest().contentType(ContentType.JSON)
               .body(body)
               .when()
               .post(BASE_PATH)
               .then()
               .statusCode(400);
    }

    @Test
    void create_returns409_whenKeyAlreadyExists() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "batch size",
                                          Instant.now()));
        String body = """
                      {"key":"chunkSize","value":"1000","valueType":"INTEGER","description":"duplicate"}
                      """;

        authenticatedRequest().contentType(ContentType.JSON)
               .body(body)
               .when()
               .post(BASE_PATH)
               .then()
               .statusCode(409);
    }

    @Test
    void update_returns200_andUpdatesValueOnly_preservingTypeAndDescription() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "batch size",
                                          Instant.now()));
        String body = """
                      {"value":"1000"}
                      """;

        authenticatedRequest().contentType(ContentType.JSON)
               .body(body)
               .when()
               .put(BASE_PATH + "/chunkSize")
               .then()
               .statusCode(200)
               .body("value", equalTo("1000"))
               .body("valueType", equalTo("INTEGER"))
               .body("description", equalTo("batch size"));

        assertPersisted("chunkSize",
                        "1000",
                        JobConfigurationValueType.INTEGER,
                        "batch size");
    }

    @Test
    void update_returns200_andOverridesTypeAndDescription_whenProvided() {
        repository.insert(
                new JobConfigurationEntry("retryEnabled",
                                          "true",
                                          JobConfigurationValueType.BOOLEAN,
                                          "old description",
                                          Instant.now()));
        String body = """
                      {"value":"false","valueType":"BOOLEAN","description":"new description"}
                      """;

        authenticatedRequest().contentType(ContentType.JSON)
               .body(body)
               .when()
               .put(BASE_PATH + "/retryEnabled")
               .then()
               .statusCode(200)
               .body("value", equalTo("false"))
               .body("description", equalTo("new description"));
    }

    @Test
    void update_returns400_whenValueInconsistentWithDeclaredType() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "batch size",
                                          Instant.now()));
        String body = """
                      {"value":"not-a-number"}
                      """;

        authenticatedRequest().contentType(ContentType.JSON)
               .body(body)
               .when()
               .put(BASE_PATH + "/chunkSize")
               .then()
               .statusCode(400);

        // The write is rejected before persisting — original value must remain
        // untouched.
        assertPersisted("chunkSize",
                        "500",
                        JobConfigurationValueType.INTEGER,
                        "batch size");
    }

    @Test
    void update_returns404_whenKeyAbsent() {
        String body = """
                      {"value":"1000"}
                      """;

        authenticatedRequest().contentType(ContentType.JSON)
               .body(body)
               .when()
               .put(BASE_PATH + "/doesNotExist")
               .then()
               .statusCode(404);
    }

    @Test
    void delete_returns204_andRemovesEntry() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "batch size",
                                          Instant.now()));

        authenticatedRequest().when()
               .delete(BASE_PATH + "/chunkSize")
               .then()
               .statusCode(204);

        org.junit.jupiter.api.Assertions.assertTrue(
                repository.findByKey("chunkSize")
                          .isEmpty());
    }

    @Test
    void delete_returns404_whenKeyAbsent() {
        authenticatedRequest().when()
               .delete(BASE_PATH + "/doesNotExist")
               .then()
               .statusCode(404);
    }

    private void assertPersisted(String key,
                                 String expectedValue,
                                 JobConfigurationValueType expectedType,
                                 String expectedDescription) {
        JobConfigurationEntry persisted = repository.findByKey(key)
                                                    .orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals(expectedValue,
                                                      persisted.value);
        org.junit.jupiter.api.Assertions.assertEquals(expectedType,
                                                      persisted.valueType);
        org.junit.jupiter.api.Assertions.assertEquals(expectedDescription,
                                                      persisted.description);
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
