package com.planet.importexport.jobconfigapi;

import java.time.Instant;

import jakarta.inject.Inject;

import com.planet.importexport.authapi.support.BearerTokenTestSupport;
import com.planet.importexport.jobconfig.JobConfigurationEntry;
import com.planet.importexport.jobconfig.JobConfigurationRepository;
import com.planet.importexport.jobconfig.JobConfigurationValueType;
import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

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

    /*
     * Same rationale as JobConfigurationRepositoryIT: the shared Quarkus test
     * context already ran JobConfigurationSeedMigration's
     * @Observes StartupEvent (seeding "chunkSize") before any test method
     * executes, so every test must start from a clean, known collection state.
     */
    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    /**
     * Clears every configuration entry after each test, so one test's
     * writes cannot leak into the next.
     */
    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    /**
     * Listing job configurations over HTTP returns every entry currently
     * stored, as a JSON array of that size.
     */
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
                              .body("$",
                                    Matchers.hasSize(2));
    }

    /**
     * Listing job configurations when the collection is empty returns 200
     * with an empty JSON array rather than an error.
     */
    @Test
    void list_returnsEmptyArray_whenNoEntries() {
        authenticatedRequest().when()
                              .get(BASE_PATH)
                              .then()
                              .statusCode(200)
                              .body("$",
                                    Matchers.hasSize(0));
    }

    /**
     * Fetching an existing key returns 200 with the entry's full
     * representation, including key, value, valueType, description, and a
     * non-null {@code updatedAt}.
     */
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
                              .body("key",
                                    Matchers.equalTo("chunkSize"))
                              .body("value",
                                    Matchers.equalTo("500"))
                              .body("valueType",
                                    Matchers.equalTo("INTEGER"))
                              .body("description",
                                    Matchers.equalTo("batch size"))
                              .body("updatedAt",
                                    Matchers.notNullValue());
    }

    /**
     * Fetching a key with no stored entry returns 404.
     */
    @Test
    void getByKey_returns404_whenAbsent() {
        authenticatedRequest().when()
                              .get(BASE_PATH + "/doesNotExist")
                              .then()
                              .statusCode(404);
    }

    /**
     * Creating a new entry returns 201 with a {@code Location} header
     * pointing at the new resource and the created representation in the
     * body, and the entry is actually persisted with the given fields.
     */
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
                              .header("Location",
                                      Matchers.containsString(BASE_PATH + "/chunkSize"))
                              .body("key",
                                    Matchers.equalTo("chunkSize"))
                              .body("value",
                                    Matchers.equalTo("500"));

        assertPersisted("chunkSize",
                        "500",
                        JobConfigurationValueType.INTEGER,
                        "batch size");
    }

    /**
     * Creating an entry whose value is inconsistent with its declared
     * valueType returns 400.
     */
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

    /**
     * Creating an entry with a required field (the key) missing from the
     * request body returns 400.
     */
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

    /**
     * Creating an entry whose key already exists returns 409 instead of
     * silently overwriting the existing entry.
     */
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

    /**
     * Updating an entry with only a new value in the request body changes
     * the value while preserving the existing valueType and description,
     * both in the response and in the persisted entry.
     */
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
                              .body("value",
                                    Matchers.equalTo("1000"))
                              .body("valueType",
                                    Matchers.equalTo("INTEGER"))
                              .body("description",
                                    Matchers.equalTo("batch size"));

        assertPersisted("chunkSize",
                        "1000",
                        JobConfigurationValueType.INTEGER,
                        "batch size");
    }

    /**
     * Updating an entry with value, valueType, and description all supplied
     * in the request body overrides all three fields in the response.
     */
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
                              .body("value",
                                    Matchers.equalTo("false"))
                              .body("description",
                                    Matchers.equalTo("new description"));
    }

    /**
     * Updating an entry with a value inconsistent with its declared type
     * returns 400, and the previously persisted value is left untouched.
     */
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

        /*
         * The write is rejected before persisting — original value must remain
         * untouched.
         */
        assertPersisted("chunkSize",
                        "500",
                        JobConfigurationValueType.INTEGER,
                        "batch size");
    }

    /**
     * Updating a key with no stored entry returns 404 instead of creating
     * one.
     */
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

    /**
     * Deleting an existing entry returns 204, and the entry is no longer
     * found in the repository afterward.
     */
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

        Assertions.assertTrue(repository.findByKey("chunkSize")
                                        .isEmpty());
    }

    /**
     * Deleting a key with no stored entry returns 404.
     */
    @Test
    void delete_returns404_whenKeyAbsent() {
        authenticatedRequest().when()
               .delete(BASE_PATH + "/doesNotExist")
               .then()
               .statusCode(404);
    }

    /**
     * Asserts that the entry stored under {@code key} matches the given
     * expected value, type, and description.
     *
     * @param key                 the entry's key to look up
     * @param expectedValue       the expected stored {@code value}
     * @param expectedType        the expected stored {@code valueType}
     * @param expectedDescription the expected stored {@code description}
     */
    private void assertPersisted(String key,
                                 String expectedValue,
                                 JobConfigurationValueType expectedType,
                                 String expectedDescription) {
        JobConfigurationEntry persisted = repository.findByKey(key)
                                                    .orElseThrow();

        Assertions.assertEquals(expectedValue, persisted.value);
        Assertions.assertEquals(expectedType, persisted.valueType);
        Assertions.assertEquals(expectedDescription, persisted.description);
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
