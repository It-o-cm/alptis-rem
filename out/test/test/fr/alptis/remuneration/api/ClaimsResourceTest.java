package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for {@link ClaimsResource} (REG-06): recording a claim linked to a contested line,
 * listing, reading the fiche, resolving, and the guards on the distributor, contested lines, entity
 * partition and access.
 */
@QuarkusTest
class ClaimsResourceTest {

    @Inject
    TestDatabaseReset reset;

    private long lineId;

    /** Resets the baseline and seeds a distributor and an Alptis commission line to contest. */
    @BeforeEach
    void seed() {
        reset.reset();
        lineId = QuarkusTransaction.requiringNew().call(() -> {
            LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
            Distributor d = new Distributor();
            d.partnerCode = "D-CLAIM";
            d.legalName = "Cabinet Réclamant";
            d.persist();
            CommissionLine line = new CommissionLine();
            line.entity = alptis;
            line.direction = Direction.OUTBOUND;
            line.status = LineStatus.EARNED;
            line.nature = LineNature.LINEAR;
            line.amount = new BigDecimal("15.00");
            line.persist();
            return line.getId();
        });
    }

    /**
     * An operator records a claim linked to a contested line, reads it, and closes it (REG-06).
     */
    @Test
    void record_list_read_and_resolve() {
        int id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"distributorCode\":\"D-CLAIM\",\"subject\":\"Taux 15 au lieu de 18\","
                        + "\"contestedLineIds\":[" + lineId + "]}")
                .when().post("/api/ui/v1/claims/ALPTIS_ASSURANCES")
                .then().statusCode(200).body("lineCount", is(1))
                .extract().jsonPath().getInt("id");

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/claims/ALPTIS_ASSURANCES")
                .then().statusCode(200).body("subject", hasItem("Taux 15 au lieu de 18"));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/claims/detail/" + id)
                .then().statusCode(200).body("lines.size()", is(1))
                .body("lines[0].nature", is("LINEAR"));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body("{\"resolution\":\"Taux corrigé, ajustement passé\"}")
                .when().post("/api/ui/v1/claims/" + id + "/resolve")
                .then().statusCode(200).body("status", is("CLOSED"));
    }

    /**
     * A claim recorded without contested lines (null ids) and with an unknown line id carries no line.
     */
    @Test
    void record_without_lines_and_with_unknown_line() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"distributorCode\":\"D-CLAIM\",\"subject\":\"Sans ligne\"}")
                .when().post("/api/ui/v1/claims/ALPTIS_ASSURANCES")
                .then().statusCode(200).body("lineCount", is(0));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"distributorCode\":\"D-CLAIM\",\"subject\":\"Ligne inconnue\",\"contestedLineIds\":[999999]}")
                .when().post("/api/ui/v1/claims/ALPTIS_ASSURANCES")
                .then().statusCode(200).body("lineCount", is(0));
    }

    /**
     * Recording a claim for an unknown distributor fails.
     */
    @Test
    void unknown_distributor_fails() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"distributorCode\":\"NOPE\",\"subject\":\"x\",\"contestedLineIds\":[]}")
                .when().post("/api/ui/v1/claims/ALPTIS_ASSURANCES")
                .then().statusCode(404);
    }

    /**
     * An unknown claim detail or resolution is a not found.
     */
    @Test
    void unknown_claim_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/claims/detail/999999").then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body("{\"resolution\":\"x\"}")
                .when().post("/api/ui/v1/claims/999999/resolve").then().statusCode(404);
    }

    /**
     * A UNEP operator cannot list Alptis claims.
     */
    @Test
    void out_of_scope_is_not_found() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/claims/ALPTIS_ASSURANCES").then().statusCode(404);
    }

    /**
     * An unrestricted auditor listing the claims of an unknown entity gets a not found.
     */
    @Test
    void unknown_entity_is_not_found() {
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/claims/GHOST").then().statusCode(404);
    }

    /**
     * A viewer may list claims but not record one.
     */
    @Test
    void viewer_reads_but_cannot_record() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/claims/ALPTIS_ASSURANCES").then().statusCode(200);
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON)
                .body("{\"distributorCode\":\"D-CLAIM\",\"subject\":\"x\",\"contestedLineIds\":[]}")
                .when().post("/api/ui/v1/claims/ALPTIS_ASSURANCES").then().statusCode(403);
    }
}
