package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionLine;
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
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for {@link ValidationResource} (WKF-02): reading a period's validation summary, giving a
 * single-level validation, the entity partition and per-role access.
 */
@QuarkusTest
class ValidationResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String MONTH = "2026-09-01";
    private static long seq = 1;

    /** Resets the baseline and seeds a steady Alptis previous and current (controlled) period. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
            Period previous = period(alptis, LocalDate.of(2026, 8, 1), PeriodState.CLOSED);
            line(alptis, previous, "1000.00");
            Period current = period(alptis, LocalDate.parse(MONTH), PeriodState.CONTROLLED);
            line(alptis, current, "1000.00");
        });
    }

    /** Creates a period of an entity at a month. */
    private static Period period(LegalEntity entity, LocalDate month, PeriodState state) {
        Period p = new Period();
        p.entity = entity;
        p.periodMonth = month;
        p.state = state;
        p.persist();
        return p;
    }

    /** Adds a linear line on a fresh account to a period. */
    private static void line(LegalEntity entity, Period period, String amount) {
        DistributorAccount account = new DistributorAccount();
        account.entity = entity;
        account.payeeType = PayeeType.PRODUCTION_CODE;
        account.payeeId = seq++;
        account.persist();
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /**
     * An operator reads the validation summary; a business validator validates the steady month.
     */
    @Test
    void reads_summary_and_validates() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/validation/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(200)
                .body("secondLevelRequired", is(false))
                .body("state", is("CONTROLLED"));

        given().auth().preemptive().basic("validator-alptis", "validator-alptis")
                .when().post("/api/ui/v1/validation/ALPTIS_ASSURANCES/" + MONTH + "/validate")
                .then().statusCode(200).body("state", is("BUSINESS_VALIDATED"));
    }

    /**
     * A UNEP operator cannot read the Alptis validation summary.
     */
    @Test
    void out_of_scope_is_not_found() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/validation/ALPTIS_ASSURANCES/" + MONTH).then().statusCode(404);
    }

    /**
     * A viewer may read the summary but not validate.
     */
    @Test
    void viewer_reads_but_cannot_validate() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/validation/ALPTIS_ASSURANCES/" + MONTH).then().statusCode(200);
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().post("/api/ui/v1/validation/ALPTIS_ASSURANCES/" + MONTH + "/validate").then().statusCode(403);
    }
}
