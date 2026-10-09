package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * HTTP tests for {@link DocumentsResource}: the statements of a period, a statement's content and
 * spreadsheet, its publication and its rectificative version, with the entity partition and access.
 */
@QuarkusTest
class DocumentsResourceTest {

    @Inject
    TestDatabaseReset reset;
    @Inject
    CycleDemoSeeder seeder;

    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds the cycle scene (statements included). */
    @BeforeEach
    void seed() {
        reset.reset();
        seeder.seed("ALPTIS_ASSURANCES", java.time.LocalDate.parse(MONTH));
    }

    /** Returns the id of the first statement. */
    private int firstStatementId() {
        return given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/ALPTIS_ASSURANCES/" + MONTH + "/statements")
                .then().statusCode(200).body("size()", greaterThanOrEqualTo(1))
                .extract().path("[0].id");
    }

    /**
     * The statement content and its spreadsheet render the beneficiary's lines.
     */
    @Test
    void renders_content_and_spreadsheet() {
        int id = firstStatementId();
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/statements/" + id)
                .then().statusCode(200).body("statementNumber", notNullValue());
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/statements/" + id + "/spreadsheet")
                .then().statusCode(200).body(containsString("rubrique;libelle"));
    }

    /**
     * A statement is published then rectified into a new version.
     */
    @Test
    void publishes_and_rectifies() {
        int id = firstStatementId();
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/documents/statements/" + id + "/publish")
                .then().statusCode(200).body("status", is("PUBLISHED"));
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/documents/statements/" + id + "/rectify")
                .then().statusCode(200).body("versionNo", is(2));
    }

    /**
     * Statements of another entity are out of scope, and an unknown statement or empty period is not
     * found.
     */
    @Test
    void out_of_scope_and_unknown() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/UNEP/" + MONTH + "/statements")
                .then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/statements/999999")
                .then().statusCode(404);
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/documents/GHOST/" + MONTH + "/statements")
                .then().statusCode(404);
    }
}
