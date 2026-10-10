package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
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
 * HTTP tests of the restitution endpoints (section 8.12): dashboards (REP-01), cross-search
 * (REP-02/03), margin (REP-08), indicators (REP-09) and the consolidated group view (REP-07),
 * with the entity partition (CR-70).
 */
@QuarkusTest
class ReportingResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String ENTITY = "ALPTIS_ASSURANCES";
    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds a period with lines of several natures. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
            Period period = new Period();
            period.entity = entity;
            period.periodMonth = LocalDate.parse(MONTH);
            period.state = PeriodState.CONTROLLED;
            period.persist();
            Distributor distributor = new Distributor();
            distributor.partnerCode = "D-R";
            distributor.legalName = "Cabinet R";
            distributor.oriasStatus = OriasStatus.REGISTERED;
            distributor.persist();
            Product product = new Product();
            product.entity = entity;
            product.code = "PROD-R";
            product.label = "Produit R";
            product.persist();
            ProductOption option = new ProductOption();
            option.product = product;
            option.persist();
            Coverage coverage = new Coverage();
            coverage.coverageCode = "COV-R";
            coverage.option = option;
            coverage.persist();
            line(entity, period, distributor, coverage, LineNature.LINEAR, LineStatus.EARNED, "1000.00", "0.10",
                    "100.00");
            line(entity, period, distributor, null, LineNature.CLAWBACK, LineStatus.EARNED, null, null, "-20.00");
            line(entity, period, distributor, null, LineNature.ADVANCE, LineStatus.EARNED, null, null, "50.00");
            line(entity, period, distributor, null, LineNature.MANUAL, LineStatus.EARNED, null, null, "10.00");
            line(entity, period, distributor, null, LineNature.LINEAR, LineStatus.OFFSET, null, null, "15.00");
        });
    }

    /** Creates a commission line. */
    private static void line(LegalEntity entity, Period period, Distributor distributor, Coverage coverage,
                             LineNature nature, LineStatus status, String basis, String rate, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = nature;
        line.status = status;
        line.originDistributor = distributor;
        line.coverage = coverage;
        line.basisAmount = basis == null ? null : new BigDecimal(basis);
        line.rate = rate == null ? null : new BigDecimal(rate);
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /**
     * The dashboard aggregates by nature, reprises, escomptes paid and offset, and the top
     * distributors.
     */
    @Test
    void dashboard_aggregates() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/dashboard?from=" + MONTH + "&to=" + MONTH)
                .then().statusCode(200)
                .body("months.size()", is(1))
                .body("byNature.label", hasItem("LINEAR"))
                .body("reprisesByMonth[0].primary", is(-20.0f))
                .body("escompteByMonth[0].primary", is(50.0f))
                .body("escompteByMonth[0].second", is(15.0f))
                .body("topDistributors.label", hasItem("D-R"));
    }

    /**
     * The indicators count the active rules and the clawback total, month by month.
     */
    @Test
    void indicators_measure_operation() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/indicators?from=" + MONTH + "&to=" + MONTH)
                .then().statusCode(200)
                .body("rows[0].activeRules", greaterThanOrEqualTo(1))
                .body("rows[0].clawback", is(-20.0f));
    }

    /**
     * The cross-search returns the counted lines and filters by nature.
     */
    @Test
    void search_filters_by_nature() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/search")
                .then().statusCode(200).body("size()", is(4));
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/search?nature=CLAWBACK&product=—&month=" + MONTH)
                .then().statusCode(200).body("size()", is(1)).body("[0].nature", is("CLAWBACK"));
    }

    /**
     * A dashboard without a range reads the recent months with data.
     */
    @Test
    void dashboard_without_range() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/dashboard")
                .then().statusCode(200).body("entity", is(ENTITY));
    }

    /**
     * The margin by product subtracts the paid from the received.
     */
    @Test
    void margin_by_product() {
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/margin")
                .then().statusCode(200).body("byProduct.key", hasItem("PROD-R"));
    }

    /**
     * Every reporting list exports as CSV at line grain (REP-04), each within its own role.
     */
    @Test
    void exports_are_csv() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/export/search")
                .then().statusCode(200).body(org.hamcrest.Matchers.containsString("distributor;product;gamme;"));
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/export/dashboard")
                .then().statusCode(200).body(org.hamcrest.Matchers.containsString("month;nature;amount"));
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/export/indicators")
                .then().statusCode(200).body(org.hamcrest.Matchers.containsString("month;active_rules;"));
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/export/margin")
                .then().statusCode(200).body(org.hamcrest.Matchers.containsString("axis;key;received;paid;margin"));
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/reporting/export/consolidated")
                .then().statusCode(200).body(org.hamcrest.Matchers.containsString("entity;month;total"));
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/reporting/export/consolidated")
                .then().statusCode(404);
    }

    /**
     * The consolidated view is reserved to cross-entity users: an auditor sees every entity, a UNEP
     * operator is out of scope, and a dashboard of another entity is not found.
     */
    @Test
    void consolidated_and_scope() {
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/reporting/consolidated")
                .then().statusCode(200).body("summaries.code", hasItem(ENTITY));
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/reporting/consolidated")
                .then().statusCode(404);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/reporting/" + ENTITY + "/dashboard")
                .then().statusCode(404);
    }
}
