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
     * The business administrator edits a document template (BRD-05); it is then listed, and a
     * non-administrator may not edit it.
     */
    @Test
    void edits_and_lists_templates() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .contentType(io.restassured.http.ContentType.JSON)
                .body("{\"documentType\":\"STATEMENT\",\"content\":\"{\\\"logo\\\":\\\"X\\\"}\"}")
                .when().post("/api/ui/v1/documents/templates/ALPTIS_ASSURANCES")
                .then().statusCode(200).body("versionNo", is(1));
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/templates/ALPTIS_ASSURANCES")
                .then().statusCode(200).body("documentType", org.hamcrest.Matchers.hasItem("STATEMENT"));
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(io.restassured.http.ContentType.JSON)
                .body("{\"documentType\":\"STATEMENT\",\"content\":\"{}\"}")
                .when().post("/api/ui/v1/documents/templates/ALPTIS_ASSURANCES")
                .then().statusCode(403);
    }

    /**
     * The annual tax certificates list answers (current year by default and for an explicit year); an
     * unknown beneficiary and an out-of-scope entity are not found (BRD-08).
     */
    @Test
    void lists_tax_certificates() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/tax-certificates/ALPTIS_ASSURANCES")
                .then().statusCode(200);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/tax-certificates/ALPTIS_ASSURANCES?year=2026")
                .then().statusCode(200);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/tax-certificates/ALPTIS_ASSURANCES/NOPE?year=2026")
                .then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/tax-certificates/ALPTIS_ASSURANCES/NOPE")
                .then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/tax-certificates/UNEP")
                .then().statusCode(404);
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

    /**
     * The informative statement of a group member lists its own share (BRD-11), guards an unknown code
     * and the entity partition.
     */
    @Test
    void member_statement_lists_share_and_guards() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/ALPTIS_ASSURANCES/" + MONTH + "/member/PC-CREDIT")
                .then().statusCode(200)
                .body("beneficiary", containsString("PC-CREDIT"))
                .body("rows.size()", greaterThanOrEqualTo(1));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/documents/ALPTIS_ASSURANCES/" + MONTH + "/member/NO-CODE")
                .then().statusCode(404);

        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/documents/ALPTIS_ASSURANCES/" + MONTH + "/member/PC-CREDIT")
                .then().statusCode(404);
    }
}
