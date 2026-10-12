package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for {@link ControlsResource}: running the controls of a period, listing its anomalies,
 * deciding on one (CTL-05) and reading the catalogue (CTL-01), with the entity partition and access.
 */
@QuarkusTest
class ControlsResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds a controlled Alptis period with one debtor account. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
            Period period = new Period();
            period.entity = alptis;
            period.periodMonth = LocalDate.parse(MONTH);
            period.state = PeriodState.CALCULATED;
            period.persist();
            ControlDefinition def = new ControlDefinition();
            def.entity = alptis;
            def.code = "ACCOUNT-DEBTOR";
            def.label = "Solde débiteur";
            def.family = ControlFamily.ACCOUNT;
            def.severity = ControlSeverity.WARNING;
            def.active = Boolean.TRUE;
            def.persist();
            DistributorAccount account = new DistributorAccount();
            account.entity = alptis;
            account.payeeType = PayeeType.DISTRIBUTOR;
            account.payeeId = 4242L;
            account.persist();
            CommissionLine line = new CommissionLine();
            line.entity = alptis;
            line.period = period;
            line.account = account;
            line.direction = Direction.OUTBOUND;
            line.status = LineStatus.EARNED;
            line.nature = LineNature.LINEAR;
            line.amount = new BigDecimal("-100");
            line.persist();
        });
    }

    /**
     * Running the controls flags the debtor account, the anomaly is listed and can be decided.
     */
    @Test
    void run_list_and_decide() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/run")
                .then().statusCode(200).body("anomalies", greaterThanOrEqualTo(1));

        Integer id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/anomalies")
                .then().statusCode(200).body("controlCode", hasItem("ACCOUNT-DEBTOR"))
                .extract().path("[0].id");

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"decision\":\"ACCEPTED\",\"justification\":\"solde repris\"}")
                .when().post("/api/ui/v1/controls/anomalies/" + id + "/decision")
                .then().statusCode(200).body("decision", is("ACCEPTED"));
    }

    /**
     * The anomaly breakdown counts the period's anomalies by severity and by control family.
     */
    @Test
    void anomaly_breakdown_counts_by_severity_and_family() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/run")
                .then().statusCode(200);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/anomaly-breakdown")
                .then().statusCode(200)
                .body("bySeverity.label", hasItem("WARNING"))
                .body("byFamily.label", hasItem("ACCOUNT"));
    }

    /**
     * The catalogue lists the entity's controls.
     */
    @Test
    void reads_catalog() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/controls/ALPTIS_ASSURANCES/catalog")
                .then().statusCode(200).body("code", hasItem("ACCOUNT-DEBTOR"));
    }

    /**
     * Running the controls of another entity's period is out of scope.
     */
    @Test
    void run_out_of_scope_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/controls/UNEP/" + MONTH + "/run")
                .then().statusCode(404);
    }

    /**
     * An unrestricted auditor reading the catalogue of an unknown entity gets a not found.
     */
    @Test
    void unknown_entity_catalog_is_not_found() {
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/controls/GHOST/catalog")
                .then().statusCode(404);
    }

    /**
     * A viewer may not run the controls.
     */
    @Test
    void viewer_cannot_run() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().post("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/run")
                .then().statusCode(403);
    }

    /**
     * The operator reads the period checklist and ticks a control as reviewed (CTL-03).
     */
    @Test
    void operator_reads_and_ticks_the_checklist() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/checklist")
                .then().statusCode(200).body("code", hasItem("ACCOUNT-DEBTOR"));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body("{\"checked\":true}")
                .when().post("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/checklist/ACCOUNT-DEBTOR")
                .then().statusCode(200).body("checked", is(true)).body("checkedBy", is("operator-alptis"));
    }

    /**
     * A viewer reads the checklist but may not tick it; a UNEP operator is out of scope for Alptis.
     */
    @Test
    void viewer_reads_checklist_but_cannot_tick_and_scope_is_enforced() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/checklist")
                .then().statusCode(200);
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON).body("{\"checked\":true}")
                .when().post("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/checklist/ACCOUNT-DEBTOR")
                .then().statusCode(403);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/controls/ALPTIS_ASSURANCES/" + MONTH + "/checklist")
                .then().statusCode(404);
    }
}
