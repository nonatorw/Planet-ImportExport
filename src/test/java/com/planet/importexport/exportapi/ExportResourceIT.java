package com.planet.importexport.exportapi;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.planet.importexport.authapi.support.BearerTokenTestSupport;
import com.planet.importexport.customerrecord.CustomerRecordRepository;
import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

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
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class ExportResourceIT {
    @Inject
    CustomerRecordRepository repository;

    @Inject
    BearerTokenTestSupport bearerTokenTestSupport;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void exportCsv_selectedColumns_returnsExactlyThoseColumnsInOrder() {
        repository.insertNextVersion(
                "1",
                Map.of("name", "John Smith",
                       "email", "john@example.com",
                       "country", "Portugal"),
                "job-1");

        String body = authenticatedRequest().contentType(ContentType.JSON)
                             .body("""
                                   {"format":"CSV","columns":["id","name","email","country"]}
                                   """)
                             .when()
                             .post("/api/v1/exports")
                             .then()
                             .statusCode(200)
                             .contentType("text/csv")
                             .extract()
                             .asString();

        assertThat(body)
                 .isEqualTo("id,name,email,country\r\n1,John Smith,john@example.com,Portugal\r\n");
    }

    @Test
    void exportTxt_selectedColumns_returnsExactlyThoseColumnsInOrder() {
        repository.insertNextVersion("1",
        Map.of("name", "John Smith",
               "email", "john@example.com"),
               "job-1");

        String body = authenticatedRequest().contentType(ContentType.JSON)
                             .body("""
                                   {"format":"TXT","columns":["name","email"]}
                                   """)
                             .when()
                             .post("/api/v1/exports")
                             .then()
                             .statusCode(200)
                             .contentType("text/plain")
                             .extract()
                             .asString();

        assertThat(body).isEqualTo("name\temail\r\nJohn Smith\tjohn@example.com\r\n");
    }

    @Test
    void exportXlsx_selectedColumns_returnsValidSpreadsheet() throws Exception {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith",
                                            "country", "Portugal"),
                                     "job-1");

        byte[] body = authenticatedRequest().contentType(ContentType.JSON)
                             .body("""
                                   {"format":"XLSX","columns":["id","name","country"]}
                                   """)
                             .when()
                             .post("/api/v1/exports")
                             .then()
                             .statusCode(200)
                             .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                             .extract()
                             .asByteArray();

        try (XSSFWorkbook workbook =
                new XSSFWorkbook(new java.io.ByteArrayInputStream(body))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0)
                            .getCell(0)
                            .getStringCellValue()).isEqualTo("id");

            assertThat(sheet.getRow(1)
                            .getCell(1)
                            .getStringCellValue()).isEqualTo("John Smith");
        }
    }

    @Test
    void exportOnlyCurrentVersion_notEveryHistoricalVersion() {
        repository.insertNextVersion("1",
                                     Map.of("country", "Portugal"),
                                     "job-1");

        repository.insertNextVersion("1",
                                     Map.of("country", "Spain"),
                                     "job-2");

        String body = authenticatedRequest().contentType(ContentType.JSON)
                             .body("""
                                   {"format":"CSV","columns":["id","country"]}
                                   """)
                             .when()
                             .post("/api/v1/exports")
                             .then()
                             .statusCode(200)
                             .extract()
                             .asString();

        assertThat(body).isEqualTo("id,country\r\n1,Spain\r\n");
    }

    @Test
    void exportRequestWithUnknownColumn_returns400NamingIt() {
        authenticatedRequest().contentType(ContentType.JSON)
               .body("""
                     {"format":"CSV","columns":["id","loyalty_tier"]}
                     """)
               .when()
               .post("/api/v1/exports")
               .then()
               .statusCode(400)
               .body("error", equalTo("unknown column"))
               .body("column", equalTo("loyalty_tier"));
    }

    @Test
    void exportRequestWithLegacyXlsFormat_isRejectedNotSilentlyFallenBack() {
        authenticatedRequest().contentType(ContentType.JSON)
               .body("""
                     {"format":"XLS","columns":["id","name"]}
                     """)
               .when()
               .post("/api/v1/exports")
               .then()
               .statusCode(400)
               .body("error", equalTo("unsupported format"));
    }

    @Test
    void exportRequestWithBlankFormat_returns400() {
        authenticatedRequest().contentType(ContentType.JSON)
               .body("""
                     {"format":"","columns":["id"]}
                     """)
               .when()
               .post("/api/v1/exports")
               .then()
               .statusCode(400);
    }

    @Test
    void exportRequestWithEmptyColumns_returns400() {
        authenticatedRequest().contentType(ContentType.JSON)
               .body("""
                     {"format":"CSV","columns":[]}
                     """)
               .when()
               .post("/api/v1/exports")
               .then()
               .statusCode(400);
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
