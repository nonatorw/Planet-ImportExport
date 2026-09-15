package com.planet.importexport.exportapi;

import java.util.Map;

import jakarta.inject.Inject;

import com.planet.importexport.authapi.support.BearerTokenTestSupport;
import com.planet.importexport.customerrecord.CustomerRecordRepository;
import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Integration test for {@code POST /api/v1/exports} (task C1-C5;
 * specs/export/spec.md), exercised end-to-end against a real (embedded)
 * MongoDB instance and the actual HTTP stack.
 *
 * <p>{@code E1} (ADR-0006) made this resource {@code @Authenticated}; every
 * request below carries a bearer token obtained from the Dev-Services
 * Keycloak realm via {@link BearerTokenTestSupport} — see
 * {@code AuthenticationIT} for the dedicated 401/200 authentication-scenario
 * coverage.</p>
 *
 * <p>{@code exportCsv_requestedOrderEmailIdName_honoredOverNaturalStorageOrder}
 * and {@code exportCsv_requestedOrderCountryName_honoredOverNaturalStorageOrder}
 * cover both example rows of {@code docs/requirements/acceptance-criteria.feature}'s
 * Scenario Outline "Requested column order is honored regardless of storage
 * order" (columns {@code ["email","id","name"]} and {@code ["country","name"]}
 * respectively), each seeding fields in the natural recognized-schema order
 * ({@code id, name, email, age, country, phone}) so the assertion proves
 * reordering, not a coincidental match with storage order.</p>
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class ExportResourceIT {
    private static final String CONTENT_TYPE_OPENXML_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Inject
    CustomerRecordRepository repository;

    @Inject
    BearerTokenTestSupport bearerTokenTestSupport;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    /**
     * A CSV export request naming a subset of columns returns exactly those
     * columns, in the requested order, as the response body.
     */
    @Test
    void exportCsv_selectedColumns_returnsExactlyThoseColumnsInOrder() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith",
                                            "email", "john@example.com",
                                            "country", "Portugal"),
                                    "job-1");

        String requestBody =
                """
                {"format":"CSV","columns":["id","name","email","country"]}
                """;

        String body =
                authenticatedRequest().contentType(ContentType.JSON)
                                      .body(requestBody)
                                      .when()
                                      .post("/api/v1/exports")
                                      .then()
                                      .statusCode(200)
                                      .contentType("text/csv")
                                      .extract()
                                      .asString();

        String expectedBody =
                """
                id,name,email,country\r
                1,John Smith,john@example.com,Portugal\r
                """;

        Assertions.assertThat(body)
                  .isEqualTo(expectedBody);
    }

    /**
     * A TXT export request naming a subset of columns returns exactly those
     * columns, tab-separated and in the requested order, as the response body.
     */
    @Test
    void exportTxt_selectedColumns_returnsExactlyThoseColumnsInOrder() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith",
                                            "email", "john@example.com"),
                                     "job-1");

        String requestBody =
                """
                {"format":"TXT","columns":["name","email"]}
                """;

        String body =
                authenticatedRequest().contentType(ContentType.JSON)
                                      .body(requestBody)
                                      .when()
                                      .post("/api/v1/exports")
                                      .then()
                                      .statusCode(200)
                                      .contentType("text/plain")
                                      .extract()
                                      .asString();

        String expectedBody =
                "name\temail\r\nJohn Smith\tjohn@example.com\r\n";

        Assertions.assertThat(body)
                  .isEqualTo(expectedBody);
    }

    /**
     * An XLSX export request returns a byte stream that can be parsed back
     * as a valid spreadsheet, with the requested columns as the header row
     * and the record's fields as the following data row.
     */
    @Test
    void exportXlsx_selectedColumns_returnsValidSpreadsheet() throws Exception {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith",
                                            "country", "Portugal"),
                                     "job-1");

        String requestBody =
                """
                {"format":"XLSX","columns":["id","name","country"]}
                """;
        byte[] body =
                authenticatedRequest().contentType(ContentType.JSON)
                                      .body(requestBody)
                                      .when()
                                      .post("/api/v1/exports")
                                      .then()
                                      .statusCode(200)
                                      .contentType(CONTENT_TYPE_OPENXML_XLSX)
                                      .extract()
                                      .asByteArray();

        try (XSSFWorkbook workbook =
                new XSSFWorkbook(new java.io.ByteArrayInputStream(body))) {

            var sheet = workbook.getSheetAt(0);

            Assertions.assertThat(sheet.getRow(0)
                                       .getCell(0)
                                       .getStringCellValue())
                      .isEqualTo("id");

            Assertions.assertThat(sheet.getRow(1)
                                       .getCell(1)
                                       .getStringCellValue())
                      .isEqualTo("John Smith");
        }
    }

    /**
     * Requesting columns {@code ["email","id","name"]} for a record whose
     * fields were seeded in the natural recognized-schema order returns the
     * CSV columns in the requested order, not the storage order, covering
     * one example row of the "Requested column order is honored regardless
     * of storage order" Scenario Outline.
     */
    @Test
    void exportCsv_requestedOrderEmailIdName_honoredOverNaturalStorageOrder() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith",
                                            "email", "john@example.com",
                                            "age", "35",
                                            "country", "Portugal"),
                                     "job-1");

        String requestBody =
                """
                {"format":"CSV","columns":["email","id","name"]}
                """;
        String body =
                authenticatedRequest().contentType(ContentType.JSON)
                                      .body(requestBody)
                                      .when()
                                      .post("/api/v1/exports")
                                      .then()
                                      .statusCode(200)
                                      .contentType("text/csv")
                                      .extract()
                                      .asString();

        String expectedBody =
                """
                email,id,name\r
                john@example.com,1,John Smith\r
                """;

        Assertions.assertThat(body)
                  .isEqualTo(expectedBody);
    }

    /**
     * Requesting columns {@code ["country","name"]} for a record whose
     * fields were seeded in the natural recognized-schema order returns the
     * CSV columns in the requested order, not the storage order, covering
     * the second example row of the "Requested column order is honored
     * regardless of storage order" Scenario Outline.
     */
    @Test
    void exportCsv_requestedOrderCountryName_honoredOverNaturalStorageOrder() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith",
                                            "email", "john@example.com",
                                            "age", "35",
                                            "country", "Portugal"),
                                     "job-1");

        String requestBody =
                """
                {"format":"CSV","columns":["country","name"]}
                """;
        String body =
                authenticatedRequest().contentType(ContentType.JSON)
                                      .body(requestBody)
                                      .when()
                                      .post("/api/v1/exports")
                                      .then()
                                      .statusCode(200)
                                      .contentType("text/csv")
                                      .extract()
                                      .asString();

        String expectedBody =
                """
                country,name\r
                Portugal,John Smith\r
                """;

        Assertions.assertThat(body)
                  .isEqualTo(expectedBody);
    }

    /**
     * When an id has more than one stored version, an export includes only
     * the current (highest) version's field values, not every historical
     * version.
     */
    @Test
    void exportOnlyCurrentVersion_notEveryHistoricalVersion() {
        repository.insertNextVersion("1",
                                     Map.of("country", "Portugal"),
                                     "job-1");

        repository.insertNextVersion("1",
                                     Map.of("country", "Spain"),
                                     "job-2");

        String requestBody =
                """
                {"format":"CSV","columns":["id","country"]}
                """;
        String body =
                authenticatedRequest().contentType(ContentType.JSON)
                                      .body(requestBody)
                                      .when()
                                      .post("/api/v1/exports")
                                      .then()
                                      .statusCode(200)
                                      .extract()
                                      .asString();

        String expectedBody =
                """
                id,country\r
                1,Spain\r
                """;

        Assertions.assertThat(body)
                  .isEqualTo(expectedBody);
    }

    /**
     * An export request naming a column that is not part of the recognized
     * schema is rejected with a 400 response whose body identifies the
     * unknown column.
     */
    @Test
    void exportRequestWithUnknownColumn_returns400NamingIt() {
        String requestBody =
                """
                {"format":"CSV","columns":["id","loyalty_tier"]}
                """;

        authenticatedRequest().contentType(ContentType.JSON)
                              .body(requestBody)
                              .when()
                              .post("/api/v1/exports")
                              .then()
                              .statusCode(400)
                              .body("error",
                                    Matchers.equalTo("unknown column"))
                              .body("column",
                                    Matchers.equalTo("loyalty_tier"));
    }

    /**
     * An export request for the legacy {@code XLS} format is rejected with a
     * 400 "unsupported format" response rather than silently falling back to
     * a supported format.
     */
    @Test
    void exportRequestWithLegacyXlsFormat_isRejectedNotSilentlyFallenBack() {
        String requestBody =
                 """
                 {"format":"XLS","columns":["id","name"]}
                 """;

        authenticatedRequest().contentType(ContentType.JSON)
                              .body(requestBody)
                              .when()
                              .post("/api/v1/exports")
                              .then()
                              .statusCode(400)
                              .body("error",
                                    Matchers.equalTo("unsupported format"));
    }

    /**
     * An export request with a blank {@code format} value is rejected with a
     * 400 response.
     */
    @Test
    void exportRequestWithBlankFormat_returns400() {
        String requestBody =
                 """
                 {"format":"","columns":["id"]}
                 """;
        authenticatedRequest().contentType(ContentType.JSON)
                              .body(requestBody)
                              .when()
                              .post("/api/v1/exports")
                              .then()
                              .statusCode(400);
    }

    /**
     * An export request with an empty {@code columns} list is rejected with
     * a 400 response.
     */
    @Test
    void exportRequestWithEmptyColumns_returns400() {
        String requestBody =
                 """
                 {"format":"CSV","columns":[]}
                 """;

        authenticatedRequest().contentType(ContentType.JSON)
                              .body(requestBody)
                              .when()
                              .post("/api/v1/exports")
                              .then()
                              .statusCode(400);
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
