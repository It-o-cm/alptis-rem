package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProductionCode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for {@link AdjustmentsResource}: submitting an adjustment (REG-01), listing a period's
 * adjustments and the second-user approval (REG-03), with the entity partition and access.
 */
@QuarkusTest
class AdjustmentsResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds a controlled Alptis period. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
            Period period = new Period();
            period.entity = alptis;
            period.periodMonth = LocalDate.parse(MONTH);
            period.state = PeriodState.CONTROLLED;
            period.persist();
        });
    }

    /** Submits a correction adjustment and returns its id. */
    private int submit(String user) {
        return given().auth().preemptive().basic(user, user)
                .contentType(ContentType.JSON)
                .body("{\"type\":\"CORRECTION\",\"amount\":9,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":42,"
                        + "\"reason\":\"taux corrigé\"}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(201).extract().path("id");
    }

    /**
     * An operator submits an adjustment, it appears in the list, and a second user approves it.
     */
    @Test
    void submit_list_and_approve() {
        int id = submit("operator-alptis");
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(200).body("status", hasItem("SUBMITTED"));
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().post("/api/ui/v1/adjustments/" + id + "/approve")
                .then().statusCode(200).body("status", is("APPLIED"));
    }

    /**
     * REG-02: an adjustment carries its mandatory reason, an attachment reference and a link to the
     * corrected commission line, all surfaced in the list.
     */
    @Test
    void submit_with_attachment_and_corrected_line() {
        Long lineId = QuarkusTransaction.requiringNew().call(() -> {
            LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
            Period period = Period.<Period>find("entity = ?1 and periodMonth = ?2", alptis, LocalDate.parse(MONTH))
                    .firstResult();
            fr.alptis.remuneration.domain.CommissionLine line = new fr.alptis.remuneration.domain.CommissionLine();
            line.entity = alptis;
            line.period = period;
            line.direction = fr.alptis.remuneration.domain.Direction.OUTBOUND;
            line.nature = fr.alptis.remuneration.domain.LineNature.LINEAR;
            line.status = fr.alptis.remuneration.domain.LineStatus.EARNED;
            line.amount = new java.math.BigDecimal("12.00");
            line.persist();
            return line.getId();
        });

        given().auth().preemptive().basic("operator-alptis", "operator-alptis").contentType(ContentType.JSON)
                .body("{\"type\":\"CORRECTION\",\"amount\":-5,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":42,"
                        + "\"reason\":\"erreur de taux\",\"attachmentRef\":\"GED-2026-07-123\",\"correctedLineId\":"
                        + lineId + "}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH).then().statusCode(201);

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(200)
                .body("attachmentRef", hasItem("GED-2026-07-123"))
                .body("correctedLineId", hasItem(lineId.intValue()));
    }

    /**
     * A transfer adjustment carries a counterpart account and applies two transfer lines.
     */
    @Test
    void submit_account_transfer_with_counterpart() {
        int id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
            .contentType(ContentType.JSON)
            .body("{\"type\":\"ACCOUNT_TRANSFER\",\"amount\":50,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":11,"
                + "\"reason\":\"transfert\",\"counterpartPayeeType\":\"DISTRIBUTOR\",\"counterpartPayeeId\":22}")
            .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH)
            .then().statusCode(201).extract().path("id");
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
            .when().post("/api/ui/v1/adjustments/" + id + "/approve")
            .then().statusCode(200).body("status", is("APPLIED"));
    }

    /**
     * Submitting into another entity's period is out of scope.
     */
    @Test
    void submit_out_of_scope_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"type\":\"CORRECTION\",\"amount\":9,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":42,"
                        + "\"reason\":\"x\"}")
                .when().post("/api/ui/v1/adjustments/UNEP/" + MONTH)
                .then().statusCode(404);
    }

    /**
     * Submitting into an unknown period is not found.
     */
    @Test
    void submit_unknown_period_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"type\":\"CORRECTION\",\"amount\":9,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":42,"
                        + "\"reason\":\"x\"}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/2020-01-01")
                .then().statusCode(404);
    }

    /**
     * An operator bulk-imports adjustments from a pivot CSV (REG-04): the well-formed line is created.
     */
    @Test
    void operator_bulk_imports_adjustments() {
        QuarkusTransaction.requiringNew().run(() -> {
            ProductionCode pc = new ProductionCode();
            pc.entity = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
            pc.code = "PC-BULK";
            pc.persist();
        });
        String csv = "adjustment_type;payee_code;amount;reason;auto_reverse\\n"
                + "CORRECTION;PC-BULK;25.00;import en masse;false\\n"
                + "CORRECTION;UNKNOWN;9.00;ignorée;false\\n";
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body("{\"content\":\"" + csv + "\"}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH + "/bulk")
                .then().statusCode(200).body("imported", is(1));
    }

    /**
     * Bulk-importing into another entity's period is out of scope.
     */
    @Test
    void bulk_out_of_scope_is_not_found() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .contentType(ContentType.JSON).body("{\"content\":\"adjustment_type\\n\"}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH + "/bulk")
                .then().statusCode(404);
    }

    /**
     * A viewer may not bulk-import adjustments.
     */
    @Test
    void viewer_cannot_bulk_import() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON).body("{\"content\":\"adjustment_type\\n\"}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH + "/bulk")
                .then().statusCode(403);
    }

    /**
     * A viewer may not submit an adjustment.
     */
    @Test
    void viewer_cannot_submit() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON)
                .body("{\"type\":\"CORRECTION\",\"amount\":9,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":42,"
                        + "\"reason\":\"x\"}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(403);
    }
}
