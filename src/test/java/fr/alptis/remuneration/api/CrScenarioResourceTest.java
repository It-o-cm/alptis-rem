package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Verifies the dev-only recette-case loader plants each case's data and that the real engine, run from
 * the calculation endpoint over the seeded period, produces the lines the specification section 13
 * expects — read on the reporting search as a user would. UNEP carries no initial barème, so a case's
 * own rule is the single applicable one. An unknown case code is refused.
 */
@QuarkusTest
class CrScenarioResourceTest {

    @Inject
    TestDatabaseReset reset;

    /** Resets the database before each test. */
    @BeforeEach
    void setUp() {
        reset.reset();
    }

    /** Plants a case and runs the real calculation of the seeded UNEP month (January 2026). */
    private static void plantAndRun(String crCode) {
        plantAndRun(crCode, "2026-01-01");
    }

    /** Plants a case and runs the real calculation of the seeded UNEP month. */
    private static void plantAndRun(String crCode, String month) {
        given().when().post("/api/test/cr-scenario?cr=" + crCode + "&entity=UNEP&month=" + month)
                .then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/" + month + "/run").then().statusCode(200);
    }

    /** The reporting search of the January 2026 UNEP month, ready for body assertions. */
    private static io.restassured.response.ValidatableResponse search() {
        return search("2026-01-01");
    }

    /** The reporting search of a UNEP month, ready for body assertions. */
    private static io.restassured.response.ValidatableResponse search(String month) {
        return given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/reporting/UNEP/search?month=" + month).then().statusCode(200);
    }

    /** CR-01: a linear 15% rule on a 100 € premium yields one LINEAR line of 15,00 €; an unknown case is refused. */
    @Test
    void cr01_linear() {
        given().when().post("/api/test/cr-scenario?cr=CR-99&entity=UNEP&month=2026-01-01").then().statusCode(400);
        plantAndRun("CR-01");
        search().body("nature", hasItem("LINEAR")).body("find { it.nature == 'LINEAR' }.amount", is(15.00f));
    }

    /** CR-02: a first-year 30/10 upfront on a 1000 € premium yields ACQUISITION 300,00 € and SERVICING 100,00 €. */
    @Test
    void cr02_upfront_first_year() {
        plantAndRun("CR-02");
        search().body("nature", hasItems("ACQUISITION", "SERVICING"))
                .body("find { it.nature == 'ACQUISITION' }.amount", is(300.00f))
                .body("find { it.nature == 'SERVICING' }.amount", is(100.00f));
    }

    /** CR-03: the same upfront garantie in its second year yields a single SERVICING line of 100,00 €, no acquisition. */
    @Test
    void cr03_upfront_second_year() {
        plantAndRun("CR-03");
        search().body("nature", hasItem("SERVICING"))
                .body("find { it.nature == 'ACQUISITION' }", nullValue())
                .body("find { it.nature == 'SERVICING' }.amount", is(100.00f));
    }

    /** CR-04: six first-year premiums then termination at day 180 — acquisition 180, servicing 60, clawback −180. */
    @Test
    void cr04_clawback_first_year() {
        plantAndRun("CR-04");
        search().body("findAll { it.nature == 'ACQUISITION' }.amount.sum() as double", is(180.0))
                .body("findAll { it.nature == 'SERVICING' }.amount.sum() as double", is(60.0))
                .body("findAll { it.nature == 'CLAWBACK' }.amount.sum() as double", is(-180.0));
    }

    /** CR-05: termination in the second year claws back 50% of the 300 € acquisition: −150,00 €. */
    @Test
    void cr05_clawback_second_year() {
        plantAndRun("CR-05");
        search().body("findAll { it.nature == 'CLAWBACK' }.amount.sum() as double", is(-150.0));
    }

    /** CR-06: termination beyond two years claws back nothing. */
    @Test
    void cr06_no_clawback_after_two_years() {
        plantAndRun("CR-06");
        search().body("findAll { it.nature == 'CLAWBACK' }.size()", is(0));
    }

    /** CR-07: a not-pursued membership claws back 100% of all earned lines (60 + 20): −80,00 €. */
    @Test
    void cr07_not_pursued() {
        plantAndRun("CR-07");
        search().body("findAll { it.nature == 'CLAWBACK' }.amount.sum() as double", is(-80.0));
    }

    /** CR-08: a death is not a breaking event, so the upfront garantie keeps its commission: no clawback. */
    @Test
    void cr08_death_no_clawback() {
        plantAndRun("CR-08");
        search().body("findAll { it.nature == 'CLAWBACK' }.size()", is(0));
    }

    /** CR-09: cancelling the CR-01 premium adds the inverse line, so the two LINEAR lines net to zero. */
    @Test
    void cr09_cancellation_is_the_inverse() {
        plantAndRun("CR-09");
        search().body("findAll { it.nature == 'LINEAR' }.amount", hasItems(15.00f, -15.00f))
                .body("findAll { it.nature == 'LINEAR' }.amount.sum() as double", is(0.0));
    }

    /** CR-10: the January premium stays at 15% (15,00 €), the March one moves to 12% (12,00 €); nothing recalculated. */
    @Test
    void cr10_version_by_event_date() {
        plantAndRun("CR-10");
        search().body("findAll { it.nature == 'LINEAR' }.amount", hasItems(15.00f, 12.00f))
                .body("findAll { it.nature == 'LINEAR' }.size()", is(2));
    }

    /** CR-11: a 12% rate outside the [15,20] grid holds the event (rate out of grid); no line is produced. */
    @Test
    void cr11_rate_out_of_grid() {
        given().when().post("/api/test/cr-scenario?cr=CR-11&entity=UNEP&month=2026-01-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/run")
                .then().statusCode(200).body("failed.holdReason", hasItem("RATE_OUT_OF_GRID"));
        search().body("size()", is(0));
    }

    /** CR-12: collective colleges pay 7% for executives and 5% for non-executives on 1000 € each: 70,00 € and 50,00 €. */
    @Test
    void cr12_colleges() {
        plantAndRun("CR-12");
        search().body("findAll { it.nature == 'LINEAR' }.amount", hasItems(70.00f, 50.00f))
                .body("findAll { it.nature == 'LINEAR' }.size()", is(2));
    }

    /** CR-20: a valid convention pays the advance on the annualised premium: two ADVANCE lines, 400,00 € total. */
    @Test
    void cr20_advance_payment() {
        plantAndRun("CR-20");
        search().body("findAll { it.nature == 'ADVANCE' }.size()", is(2))
                .body("findAll { it.nature == 'ADVANCE' }.amount.sum() as double", is(400.0));
    }

    /** The discount position of a convention, ready for body assertions. */
    private static io.restassured.response.ValidatableResponse position(String partner) {
        return given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/advance-agreements/UNEP/" + partner).then().statusCode(200);
    }

    /** CR-21: two premiums are offset against the advance; the position stays active with 480 advanced, 80 offset. */
    @Test
    void cr21_apurement() {
        plantAndRun("CR-21");
        position("P-CR21").body("positions[0].status", is("ACTIVE"))
                .body("positions[0].advanced as double", is(480.0))
                .body("positions[0].offset as double", is(80.0));
    }

    /** CR-22: a termination at six months cancels the advance, regularises −200,00 € and claws back −150,00 €. */
    @Test
    void cr22_rupture_with_clawback() {
        plantAndRun("CR-22");
        search().body("findAll { it.nature == 'ADVANCE_ADJUSTMENT' }.amount.sum() as double", is(-200.0))
                .body("findAll { it.nature == 'CLAWBACK' }.amount.sum() as double", is(-150.0));
        position("P-CR22").body("positions[0].status", is("CANCELLED"));
    }

    /** CR-23: once the offset cumul reaches the advance, the position settles with no adjustment. */
    @Test
    void cr23_settled_by_offset() {
        plantAndRun("CR-23");
        position("P-CR23").body("positions[0].status", is("SETTLED"));
        search().body("findAll { it.nature == 'ADVANCE_ADJUSTMENT' }.size()", is(0));
    }

    /** CR-24: a hike past the advance settles it with a payable top-up of +30,00 €. */
    @Test
    void cr24_top_up_complement() {
        plantAndRun("CR-24");
        search().body("findAll { it.nature == 'ADVANCE_ADJUSTMENT' }.amount.sum() as double", is(30.0));
        position("P-CR24").body("positions[0].status", is("SETTLED"));
    }

    /** CR-25: a garantie effective after the convention window gets no advance; its premiums are paid. */
    @Test
    void cr25_outside_convention() {
        plantAndRun("CR-25");
        search().body("findAll { it.nature == 'ADVANCE' }.size()", is(0))
                .body("size()", is(2));
    }

    /** CR-26: a rank-2 unpaid notice breaks the advance at once: position cancelled, −400,00 € regularised. */
    @Test
    void cr26_second_unpaid() {
        plantAndRun("CR-26");
        search().body("findAll { it.nature == 'ADVANCE_ADJUSTMENT' }.amount.sum() as double", is(-400.0));
        position("P-CR26").body("positions[0].status", is("CANCELLED"));
    }

    /** CR-27: beyond the convention cap the garantie gets no advance; its premiums are paid. */
    @Test
    void cr27_cap_reached() {
        plantAndRun("CR-27");
        search().body("findAll { it.nature == 'ADVANCE' }.size()", is(0))
                .body("size()", is(2));
    }

    /** CR-28: a shortfall still open at the 360-day due date is clawed back as an undue advance of −30,00 €. */
    @Test
    void cr28_undue_advance_at_due_date() {
        plantAndRun("CR-28", "2026-12-01");
        search("2026-12-01").body("findAll { it.nature == 'ADVANCE_ADJUSTMENT' }.amount.sum() as double", is(-30.0));
        position("P-CR28").body("positions[0].status", is("SETTLED"));
    }

    /** CR-30: an initial retirement payment of 6000 € at 2% yields one LINEAR line of 120,00 €. */
    @Test
    void cr30_initial_payment() {
        plantAndRun("CR-30");
        search().body("findAll { it.nature == 'LINEAR' }.size()", is(1))
                .body("find { it.nature == 'LINEAR' }.amount", is(120.00f));
    }

    /** CR-31: a scheduled retirement payment of 300 € at 3% yields one LINEAR line of 9,00 €. */
    @Test
    void cr31_scheduled_payment_linear() {
        plantAndRun("CR-31");
        search().body("findAll { it.nature == 'LINEAR' }.size()", is(1))
                .body("find { it.nature == 'LINEAR' }.amount", is(9.00f));
    }

    /** CR-32: a discounted scheduled plan of 300 €/month yields one ADVANCE line of 288,00 € in year one. */
    @Test
    void cr32_discount_year_one() {
        plantAndRun("CR-32", "2026-02-01");
        search("2026-02-01").body("size()", is(1))
                .body("find { it.nature == 'ADVANCE' }.amount", is(288.00f));
    }
}
