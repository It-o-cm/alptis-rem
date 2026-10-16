package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
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

    /** The anomalies of a UNEP period, ready for body assertions. */
    private static io.restassured.response.ValidatableResponse anomalies(String month) {
        return given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/controls/UNEP/" + month + "/anomalies").then().statusCode(200);
    }

    /** CR-33: a suspended discounted plan keeps an expected insurer-commission recall of −71,25 €. */
    @Test
    void cr33_expected_insurer_recall() {
        plantAndRun("CR-33");
        anomalies("2026-01-01").body("find { it.controlCode == 'CAL-13' }.expectedValue as double", is(-71.25));
    }

    /** CR-34: a suspended discounted plan keeps an expected insurer-commission recall of −158,10 €. */
    @Test
    void cr34_expected_insurer_recall() {
        plantAndRun("CR-34");
        anomalies("2026-01-01").body("find { it.controlCode == 'CAL-13' }.expectedValue as double", is(-158.1));
    }

    /** CR-36: the annual close keeps an expected insurer surcommission of 1 008,00 €, no line. */
    @Test
    void cr36_expected_surcommission() {
        given().when().post("/api/test/cr-scenario?cr=CR-36&entity=UNEP&month=2026-12-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-12-01/close-annual").then().statusCode(200);
        search("2026-12-01").body("size()", is(0));
        anomalies("2026-12-01").body("find { it.controlCode == 'CAL-13' }.expectedValue as double", is(1008.0));
    }

    /**
     * CR-35: closing the year on a distributor whose production stays under 10 000 € applies the 10% tier
     * to each contract, giving COMPLEMENT lines of 351,00 € and 352,98 €, 703,98 € in all.
     */
    @Test
    void cr35_annual_complement() {
        given().when().post("/api/test/cr-scenario?cr=CR-35&entity=UNEP&month=2026-12-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-12-01/close-annual").then().statusCode(200);
        search("2026-12-01").body("findAll { it.nature == 'COMPLEMENT' }.size()", is(2))
                .body("findAll { it.nature == 'COMPLEMENT' }.amount", hasItems(351.00f, 352.98f));
    }

    /** CR-42: a 120 000 € cumulative production yields one RCD line of 6 000,00 € at the development close. */
    @Test
    void cr42_development_whole_tier() {
        given().when().post("/api/test/cr-scenario?cr=CR-42&entity=UNEP&month=2026-01-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/close-development").then().statusCode(200);
        search().body("findAll { it.nature == 'RCD' }.size()", is(1))
                .body("find { it.nature == 'RCD' }.amount", is(6000.00f));
    }

    /** CR-46: a 4% portfolio rule on 200 000 € of premiums, conditions met, yields an RCP line of 8 000,00 €. */
    @Test
    void cr46_portfolio_health() {
        given().when().post("/api/test/cr-scenario?cr=CR-46&entity=UNEP&month=2026-12-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-12-01/close-portfolio").then().statusCode(200);
        search("2026-12-01").body("findAll { it.nature == 'RCP' }.size()", is(1))
                .body("find { it.nature == 'RCP' }.amount", is(8000.00f));
    }

    /** CR-41: cumulative development closes pay 300,00 € in January and 1 050,00 € in February. */
    @Test
    void cr41_development_two_tiers() {
        given().when().post("/api/test/cr-scenario?cr=CR-41&entity=UNEP&month=2026-01-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/close-development").then().statusCode(200);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-02-01/close-development").then().statusCode(200);
        search("2026-01-01").body("find { it.nature == 'RCD' }.amount", is(300.00f));
        search("2026-02-01").body("find { it.nature == 'RCD' }.amount", is(1050.00f));
    }

    /** CR-50: a 60% retrocession on a 100 € received insurer commission yields a RETROCESSION line of 60,00 €. */
    @Test
    void cr50_mandatary_retrocession() {
        plantAndRun("CR-50");
        search().body("findAll { it.nature == 'RETROCESSION' }.size()", is(1))
                .body("find { it.nature == 'RETROCESSION' }.amount", is(60.00f));
    }

    /** The explanation of a commission line, read on the line-detail endpoint. */
    private static io.restassured.response.ValidatableResponse lineDetail(int id) {
        return given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/commission-lines/" + id).then().statusCode(200);
    }

    /** Advances a UNEP period to a target state as a given user. */
    private static void transition(String user, String month, String toState) {
        given().auth().preemptive().basic(user, user).contentType("application/json")
                .body(java.util.Map.of("toState", toState, "reason", "test"))
                .when().post("/api/ui/v1/periods/UNEP/" + month + "/transition").then().statusCode(200);
    }

    /** CR-63: a 7,50 € commission below the 10 € payment threshold yields no order and a below-threshold exclusion. */
    @Test
    void cr63_below_threshold() {
        plantAndRun("CR-63");
        transition("admin-unep", "2026-01-01", "CALCULATED");
        transition("operator-unep", "2026-01-01", "CONTROLLED");
        transition("validator-unep", "2026-01-01", "BUSINESS_VALIDATED");
        transition("operator-unep", "2026-01-01", "PAYMENT_PREPARED");
        int lotId = given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/UNEP/2026-01-01/lots").then().statusCode(200)
                .body("size()", is(1)).extract().path("[0].id");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/lots/" + lotId).then().statusCode(200)
                .body("orders.size()", is(0))
                .body("exclusions.reason", hasItem("BELOW_THRESHOLD"))
                .body("exclusions.find { it.reason == 'BELOW_THRESHOLD' }.amount", is(7.50f));
    }

    /** The accounting entries of a UNEP period, ready for body assertions. */
    private static io.restassured.response.ValidatableResponse entries(String month) {
        return given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/accounting/UNEP/" + month + "/entries").then().statusCode(200);
    }

    /** CR-65: the accounting cycle posts the forecast provision of 180, the 15 earning/payable, then the 15 payment. */
    @Test
    void cr65_accounting_cycle() {
        given().when().post("/api/test/cr-scenario?cr=CR-65&entity=UNEP&month=2026-01-01").then().statusCode(204);
        transition("admin-unep", "2026-01-01", "CALCULATED");
        transition("operator-unep", "2026-01-01", "CONTROLLED");
        transition("validator-unep", "2026-01-01", "BUSINESS_VALIDATED");
        entries("2026-01-01")
                .body("find { it.accountingEvent == 'FORECAST_RECOGNITION' }.amount", is(180.00f))
                .body("find { it.accountingEvent == 'EARNING' }.amount", is(15.00f))
                .body("find { it.accountingEvent == 'PAYABLE_RECOGNITION' }.amount", is(15.00f));
        // Prepare, validate and settle the payment, which posts the 15,00 € payment entry.
        transition("operator-unep", "2026-01-01", "PAYMENT_PREPARED");
        int lotId = given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/UNEP/2026-01-01/lots").then().statusCode(200)
                .body("size()", is(1)).extract().path("[0].id");
        int orderId = given().auth().preemptive().basic("finance-unep", "finance-unep")
                .when().post("/api/ui/v1/payment/lots/" + lotId + "/validate").then().statusCode(200)
                .body("orders.size()", is(1)).extract().path("orders[0].id");
        given().auth().preemptive().basic("finance-unep", "finance-unep").contentType("application/json")
                .body(java.util.Map.of("settled", true))
                .when().post("/api/ui/v1/payment/orders/" + orderId + "/result").then().statusCode(200);
        entries("2026-01-01").body("find { it.accountingEvent == 'PAYMENT' }.amount", is(15.00f));
    }

    /** CR-66: a sixth-month termination posts a forecast-adjustment entry of 90,00 € at business validation. */
    @Test
    void cr66_termination_forecast_adjustment() {
        given().when().post("/api/test/cr-scenario?cr=CR-66&entity=UNEP&month=2026-01-01").then().statusCode(204);
        transition("admin-unep", "2026-01-01", "CALCULATED");
        transition("operator-unep", "2026-01-01", "CONTROLLED");
        transition("validator-unep", "2026-01-01", "BUSINESS_VALIDATED");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/accounting/UNEP/2026-01-01/entries").then().statusCode(200)
                .body("find { it.accountingEvent == 'FORECAST_ADJUSTMENT' }.amount", is(90.00f));
    }

    /** The single payment lot of a UNEP period, returning its detail path after preparing to PAYMENT_PREPARED. */
    private static void prepareMonth(String month) {
        transition("admin-unep", month, "CALCULATED");
        transition("operator-unep", month, "CONTROLLED");
        transition("validator-unep", month, "BUSINESS_VALIDATED");
        transition("operator-unep", month, "PAYMENT_PREPARED");
    }

    /** CR-62: a struck-off distributor's cycle payment is cancelled, then an off-cycle lot pays 110,00 € once reinstated. */
    @Test
    void cr62_orias_suspension_and_off_cycle() {
        given().when().post("/api/test/cr-scenario?cr=CR-62&entity=UNEP&month=2026-01-01").then().statusCode(204);
        prepareMonth("2026-01-01");
        int lotId = given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/UNEP/2026-01-01/lots").then().statusCode(200)
                .body("size()", is(1)).extract().path("[0].id");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/payment/lots/" + lotId + "/prepare").then().statusCode(200)
                .body("orders[0].status", is("CANCELLED"))
                .body("exclusions.reason", hasItem("PAYEE_INACTIVE"));
        // Reinstatement: re-import the distributor referential with the ORIAS status back to registered.
        String dist = "entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from;"
                + "orias_status\nUNEP;SESAME;P-CR62;P-CR62;INDEPENDENT;ACTIVE;2026-01-01;REGISTERED\n";
        given().auth().preemptive().basic("operator-unep", "operator-unep").contentType("application/json")
                .body(java.util.Map.of("entityCode", "UNEP", "flowCode", "distributors", "sourceSystem", "SESAME",
                        "fileName", "reinstate.csv", "content", dist))
                .when().post("/api/ui/v1/imports").then().statusCode(201).body("linesLoaded", is(1));
        // The off-cycle lot now pays the 110,00 € that was blocked.
        int offId = given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/payment/UNEP/2026-01-01/off-cycle/P-CR62").then().statusCode(200)
                .body("total", is(110.00f)).extract().path("id");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/lots/" + offId).then().statusCode(200)
                .body("orders.size()", is(1)).body("orders[0].amount", is(110.00f));
    }

    /** CR-60: a −1 000 € opening balance means no payment in February, then a 300,00 € order in March. */
    @Test
    void cr60_debtor_carried_forward() {
        given().when().post("/api/test/cr-scenario?cr=CR-60&entity=UNEP&month=2026-02-01").then().statusCode(204);
        prepareMonth("2026-02-01");
        int febLot = given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/UNEP/2026-02-01/lots").then().statusCode(200)
                .body("size()", is(1)).extract().path("[0].id");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/lots/" + febLot).then().statusCode(200).body("orders.size()", is(0));

        prepareMonth("2026-03-01");
        int marLot = given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/UNEP/2026-03-01/lots").then().statusCode(200)
                .body("size()", is(1)).extract().path("[0].id");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/lots/" + marLot).then().statusCode(200)
                .body("orders.size()", is(1)).body("orders[0].amount", is(300.00f));
    }

    /** CR-61: compensating a distributor's −200 and +500 codes writes two TRANSFER lines and pays 300,00 €. */
    @Test
    void cr61_compensation() {
        given().when().post("/api/test/cr-scenario?cr=CR-61&entity=UNEP&month=2026-01-01").then().statusCode(204);
        transition("admin-unep", "2026-01-01", "CALCULATED");
        transition("operator-unep", "2026-01-01", "CONTROLLED");
        transition("validator-unep", "2026-01-01", "BUSINESS_VALIDATED");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/accounts/UNEP/2026-01-01/distributor/P-CR61/compensate")
                .then().statusCode(200).body("transfers", is(2));
        transition("operator-unep", "2026-01-01", "PAYMENT_PREPARED");
        search().body("findAll { it.nature == 'TRANSFER' }.size()", is(2));
        int lotId = given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/UNEP/2026-01-01/lots").then().statusCode(200)
                .body("size()", is(1)).extract().path("[0].id");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/lots/" + lotId).then().statusCode(200)
                .body("orders.size()", is(1)).body("orders[0].amount", is(300.00f));
    }

    /** CR-64: each order aligns with a bordereau of the same amount and the lot total equals their sum. */
    @Test
    void cr64_alignment() {
        plantAndRun("CR-01");
        transition("admin-unep", "2026-01-01", "CALCULATED");
        transition("operator-unep", "2026-01-01", "CONTROLLED");
        transition("validator-unep", "2026-01-01", "BUSINESS_VALIDATED");
        transition("operator-unep", "2026-01-01", "PAYMENT_PREPARED");
        int lotId = given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/UNEP/2026-01-01/lots").then().statusCode(200)
                .body("size()", is(1)).body("[0].total", is(15.00f)).extract().path("[0].id");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/payment/lots/" + lotId).then().statusCode(200)
                .body("orders.size()", is(1)).body("orders[0].amount", is(15.00f)).body("total", is(15.00f));
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/documents/UNEP/2026-01-01/statements").then().statusCode(200)
                .body("netAmount", hasItem(15.00f));
    }

    /** CR-69: simulating the CR-01 rule on its calculated period reproduces the line to the cent (ecart 0). */
    @Test
    void cr69_reproduces_identical_lines() {
        plantAndRun("CR-01");
        given().auth().preemptive().basic("operator-unep", "operator-unep").contentType("application/json")
                .body(java.util.Map.of("ruleCode", "R-CR01-LIN", "versionNo", 1,
                        "substitutions", java.util.List.of()))
                .when().post("/api/ui/v1/simulation/UNEP/2026-01-01")
                .then().statusCode(200)
                .body("realTotal", is(15.00f)).body("simulatedTotal", is(15.00f))
                .body("ecart", is(0.00f)).body("linesReplayed", is(1)).body("linesModified", is(0));
    }

    /** CR-37: the UMR file mapping keeps only the entity's (ALPTIS) part, dropping the UMR line. */
    @Test
    void cr37_only_the_entity_part_is_integrated() {
        given().when().post("/api/test/cr-scenario?cr=CR-37&entity=UNEP&month=2026-07-01").then().statusCode(204);
        String content = "CODE_MOUVEMENT;TYPE_MOUVEMENT;NUMERO_CONTRAT;DATE_MOUVEMENT;NOM_MUTUELLE;MONTANT_COMMISSION\n"
                + "M1;FG;AD-1;2026-07-05;ALPTIS;0.24\n"
                + "M1;FG;AD-1;2026-07-05;UMR;0.19\n";
        given().auth().preemptive().basic("operator-unep", "operator-unep").contentType("application/json")
                .body(java.util.Map.of("entityCode", "UNEP", "flowCode", "events", "sourceSystem", "UMR",
                        "fileName", "umr.xlsx", "content", content))
                .when().post("/api/ui/v1/imports")
                .then().statusCode(201)
                .body("linesRead", is(2)).body("linesFiltered", is(1)).body("linesLoaded", is(1));
    }

    /** CR-38: the UMR July file's received commissions load as INSURER lines totalling 451,54 €. */
    @Test
    void cr38_received_commissions_match_the_bordereau_total() {
        given().when().post("/api/test/cr-scenario?cr=CR-38&entity=UNEP&month=2026-07-01").then().statusCode(204);
        String content = "CODE_MOUVEMENT;TYPE_MOUVEMENT;NUMERO_CONTRAT;DATE_MOUVEMENT;NOM_MUTUELLE;MONTANT_COMMISSION\n"
                + "M10;VI;AD-1;2026-07-05;ALPTIS;449.08\n"
                + "M11;FG;AD-1;2026-07-10;ALPTIS;2.46\n";
        given().auth().preemptive().basic("operator-unep", "operator-unep").contentType("application/json")
                .body(java.util.Map.of("entityCode", "UNEP", "flowCode", "events", "sourceSystem", "UMR",
                        "fileName", "umr_prod_202607.xlsx", "content", content))
                .when().post("/api/ui/v1/imports").then().statusCode(201).body("linesLoaded", is(2));
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-07-01/run").then().statusCode(200);
        search("2026-07-01").body("findAll { it.nature == 'INSURER' }.size()", is(2))
                .body("findAll { it.nature == 'INSURER' }.amount", hasItems(449.08f, 2.46f));
    }

    /** CR-67: a bad-line events file loads 1 line, rejects 3 and holds 1 (unknown contract). */
    @Test
    void cr67_bad_lines_rejected_or_held() {
        given().when().post("/api/test/cr-scenario?cr=CR-67&entity=UNEP&month=2026-01-01").then().statusCode(204);
        String header = "entity_code;source_system;source_key;event_type;contract_number;coverage_code;"
                + "event_date;gross_amount";
        String content = header + "\n"
                + "UNEP;CLEVA;ENC-1;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00\n"
                + "UNEP;CLEVA;ENC-2;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;0.00\n"
                + "UNEP;CLEVA;ENC-3;PREMIUM_COLLECTED;;C1;2020-01-05;100.00\n"
                + "UNEP;CLEVA;ENC-4;PREMIUM_COLLECTED;AD-1;C1;2999-01-01;100.00\n"
                + "UNEP;CLEVA;ENC-5;PREMIUM_COLLECTED;AD-UNKNOWN;C1;2020-01-05;100.00\n";
        given().auth().preemptive().basic("operator-unep", "operator-unep").contentType("application/json")
                .body(java.util.Map.of("entityCode", "UNEP", "flowCode", "events", "sourceSystem", "CLEVA",
                        "fileName", "unep_events.csv", "content", content))
                .when().post("/api/ui/v1/imports")
                .then().statusCode(201)
                .body("linesRead", is(5)).body("linesLoaded", is(1))
                .body("linesRejected", is(3)).body("linesOnHold", is(1));
    }

    /** CR-45: an adhésion bearing 40 € of paid RCD, terminated in the 8th month, is clawed back in full. */
    @Test
    void cr45_development_reprise() {
        plantAndRun("CR-45", "2026-09-01");
        search("2026-09-01").body("findAll { it.nature == 'CLAWBACK' }.amount.sum() as double", is(-40.0));
    }

    /** CR-40: a 15 000 € production stays under the first tier, so the development close writes no line. */
    @Test
    void cr40_development_below_first_tier() {
        given().when().post("/api/test/cr-scenario?cr=CR-40&entity=UNEP&month=2026-01-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/close-development").then().statusCode(200);
        search().body("findAll { it.nature == 'RCD' }.size()", is(0));
    }

    /** CR-44: an unsigned amendment produces no line and raises an ENR-01 control anomaly. */
    @Test
    void cr44_development_without_signed_amendment() {
        given().when().post("/api/test/cr-scenario?cr=CR-44&entity=UNEP&month=2026-01-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/close-development").then().statusCode(200);
        search().body("findAll { it.nature == 'RCD' }.size()", is(0));
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/controls/UNEP/2026-01-01/anomalies")
                .then().statusCode(200).body("controlCode", hasItem("ENR-01"));
    }

    /** CR-43: an unpaid rate of 6% forfeits the development line — a 0,00 € RCD line keeping its reason. */
    @Test
    void cr43_development_unpaid_condition() {
        given().when().post("/api/test/cr-scenario?cr=CR-43&entity=UNEP&month=2026-01-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/close-development").then().statusCode(200);
        int id = search().body("findAll { it.nature == 'RCD' }.size()", is(1))
                .body("find { it.nature == 'RCD' }.amount", is(0.00f))
                .extract().path("find { it.nature == 'RCD' }.id");
        lineDetail(id).body("explanation", containsString("forfeited"));
    }

    /** CR-47: a P/C of 96% breaks the portfolio condition — a 0,00 € RCP line keeping its condition detail. */
    @Test
    void cr47_portfolio_condition_failed() {
        given().when().post("/api/test/cr-scenario?cr=CR-47&entity=UNEP&month=2026-12-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-12-01/close-portfolio").then().statusCode(200);
        int id = search("2026-12-01").body("findAll { it.nature == 'RCP' }.size()", is(1))
                .body("find { it.nature == 'RCP' }.amount", is(0.00f))
                .extract().path("find { it.nature == 'RCP' }.id");
        lineDetail(id).body("explanation", containsString("LOSS_RATIO"));
    }

    /** CR-48: 12 new adhésions reach the 2% count tier, giving an RCP line of 1 000,00 € on 50 000 €. */
    @Test
    void cr48_portfolio_provident_tier() {
        given().when().post("/api/test/cr-scenario?cr=CR-48&entity=UNEP&month=2026-12-01").then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-12-01/close-portfolio").then().statusCode(200);
        search("2026-12-01").body("findAll { it.nature == 'RCP' }.size()", is(1))
                .body("find { it.nature == 'RCP' }.amount", is(1000.00f));
    }

    /** CR-49: a 30 € linear commission split 90/10 pays 27,00 € to the distributor and 3,00 € to its group. */
    @Test
    void cr49_share_with_group() {
        plantAndRun("CR-49");
        search().body("findAll { it.nature == 'LINEAR' }.size()", is(2))
                .body("findAll { it.nature == 'LINEAR' }.amount", hasItems(27.00f, 3.00f));
    }

    /** CR-51: an external line is attributed as-is — one EXTERNAL line of 17,50 €, no recalculation. */
    @Test
    void cr51_external() {
        plantAndRun("CR-51");
        search().body("findAll { it.nature == 'EXTERNAL' }.size()", is(1))
                .body("find { it.nature == 'EXTERNAL' }.amount", is(17.50f));
    }

    /**
     * CR-73: a quarterly premium straddling the first-year boundary prorates the acquisition by day —
     * ACQUISITION 28,31 € and SERVICING 30,00 €, in the February 2027 period.
     */
    @Test
    void cr73_prorata_at_the_boundary() {
        plantAndRun("CR-73", "2027-02-01");
        search("2027-02-01").body("nature", hasItems("ACQUISITION", "SERVICING"))
                .body("find { it.nature == 'ACQUISITION' }.amount", is(28.31f))
                .body("find { it.nature == 'SERVICING' }.amount", is(30.00f));
    }

    /**
     * CR-74: a rate change from 15% to 18% on 16 June splits June's 100 € premium pro rata of the days
     * (P12) — two LINEAR lines of 7,50 € and 9,00 €.
     */
    @Test
    void cr74_rate_change_within_covered_period() {
        plantAndRun("CR-74", "2026-06-01");
        search("2026-06-01").body("findAll { it.nature == 'LINEAR' }.size()", is(2))
                .body("findAll { it.nature == 'LINEAR' }.amount", hasItems(7.50f, 9.00f));
    }

    /**
     * CR-75: a dérogatoire rule of higher priority replaces the base rule (18% not 15%) and a back-to-
     * school operation in another group adds 2% — two LINEAR lines of 18,00 € and 2,00 €.
     */
    @Test
    void cr75_exclusion_groups_and_operation() {
        plantAndRun("CR-75");
        search().body("findAll { it.nature == 'LINEAR' }.size()", is(2))
                .body("findAll { it.nature == 'LINEAR' }.amount", hasItems(18.00f, 2.00f));
    }

    /** CR-77: a fixed fee of 300 € yields a FEE line of 300,00 €, with neither rate nor basis. */
    @Test
    void cr77_fixed_fee() {
        plantAndRun("CR-77");
        search().body("findAll { it.nature == 'FEE' }.size()", is(1))
                .body("find { it.nature == 'FEE' }.amount", is(300.00f));
    }

    /** CR-82: a 0,30% annual rate on a 20 000 € average balance over a quarter yields OUTSTANDING 15,00 €. */
    @Test
    void cr82_outstanding() {
        plantAndRun("CR-82");
        search().body("findAll { it.nature == 'OUTSTANDING' }.size()", is(1))
                .body("find { it.nature == 'OUTSTANDING' }.amount", is(15.00f));
    }

    /**
     * CR-83: nothing on the first arbitrage of the calendar year, an ARBITRAGE line of 30,00 € on the
     * second (0,30% of 10 000 €).
     */
    @Test
    void cr83_arbitrage_second_of_year() {
        plantAndRun("CR-83");
        search().body("findAll { it.nature == 'ARBITRAGE' }.size()", is(1))
                .body("find { it.nature == 'ARBITRAGE' }.amount", is(30.00f));
    }

    /**
     * CR-81: a borrower early repayment within the 36-month window recalculates linearly and claws back
     * one CLAWBACK line of −200,00 €.
     */
    @Test
    void cr81_early_repayment_clawback() {
        plantAndRun("CR-81");
        search().body("findAll { it.nature == 'CLAWBACK' }.size()", is(1))
                .body("find { it.nature == 'CLAWBACK' }.amount", is(-200.00f));
    }

    /**
     * CR-79: a 120-day anticipation delay defers the discount to the February 2026 period — two ADVANCE
     * lines (acquisition 300,00 € and servicing 100,00 €) on a 1 000 € annualised premium.
     */
    @Test
    void cr79_anticipation_delay() {
        plantAndRun("CR-79", "2026-02-01");
        search("2026-02-01").body("findAll { it.nature == 'ADVANCE' }.size()", is(2))
                .body("findAll { it.nature == 'ADVANCE' }.amount", hasItems(300.00f, 100.00f));
    }

    /**
     * CR-84: at the first anniversary, 4 200 € collected against a 3 600 € base gives a discount
     * supplement of 48,00 € as one ADVANCE line.
     */
    @Test
    void cr84_annual_readjustment_supplement() {
        plantAndRun("CR-84");
        search().body("findAll { it.nature == 'ADVANCE' }.size()", is(1))
                .body("find { it.nature == 'ADVANCE' }.amount", is(48.00f));
    }

    /**
     * CR-76: a 100 € commission co-broked 60/40, the main broker's share split 90/10 with its group, then
     * cancelled, yields six lines — 54,00 €, 6,00 €, 40,00 € and their reversals −54,00 €, −6,00 €, −40,00 €.
     */
    @Test
    void cr76_co_broking_and_group() {
        plantAndRun("CR-76");
        search().body("amount", hasItems(54.00f, 6.00f, 40.00f, -54.00f, -6.00f, -40.00f));
    }

    /**
     * CR-85: the bank-rejection scenario plants a payable 110 € earned line with bank details; the
     * pipeline and the rejection itself are pinned by PaymentServiceTest. Here the seed is exercised and
     * the 110 € line read on the reporting search.
     */
    @Test
    void cr85_bank_rejection_scenario_is_seeded() {
        given().when().post("/api/test/cr-scenario?cr=CR-85&entity=UNEP&month=2026-01-01").then().statusCode(204);
        search().body("findAll { it.nature == 'LINEAR' }.amount", hasItem(110.00f));
    }

    /**
     * CR-78: a 0,04 € shortfall under the 1 € settlement tolerance settles the discount with no
     * ADVANCE_ADJUSTMENT line (the write-off itself is pinned by AccountingServiceTest).
     */
    @Test
    void cr78_settlement_tolerance_writes_off_without_adjustment() {
        plantAndRun("CR-78", "2026-12-01");
        search("2026-12-01").body("findAll { it.nature == 'ADVANCE_ADJUSTMENT' }.size()", is(0))
                .body("nature", hasItem("ADVANCE"));
    }

    /**
     * CR-80 (variant repaid by the transferor): the February cession of a discounted garantie cancels the
     * position and regularises the un-offset part by a −280,00 € ADVANCE_ADJUSTMENT line on the
     * transferor's account. January opens the discount, February applies the cession.
     */
    @Test
    void cr80_cession_repaid_by_transferor() {
        plantAndRun("CR-80", "2026-01-01");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-02-01/run").then().statusCode(200);
        search("2026-02-01").body("findAll { it.nature == 'ADVANCE_ADJUSTMENT' }.amount", hasItem(-280.00f));
    }

    /**
     * CR-86: the retirement-savings scenario plants a 110 € cycle line and a 50 € savings line; the
     * two-lot split and the period's PAID gating are pinned by AccountServiceTest and PaymentServiceTest.
     * Here the seed is exercised and both lines read on the reporting search.
     */
    @Test
    void cr86_savings_lot_scenario_is_seeded() {
        given().when().post("/api/test/cr-scenario?cr=CR-86&entity=UNEP&month=2026-01-01").then().statusCode(204);
        search().body("findAll { it.nature == 'LINEAR' }.amount", hasItems(110.00f, 50.00f));
    }

    /**
     * CAL-07 (parcours): two concurrent base rules hold the event with no line produced (the hold reason
     * itself is pinned by CalculationEngineTest).
     */
    @Test
    void cal07_concurrent_rules_produce_no_line() {
        plantAndRun("CAL-07");
        search().body("size()", is(0));
    }

    /**
     * CAL-12 (parcours): a cancellation after a rate change computes the inverse line with the original
     * version — a 15,00 € line and a −15,00 € inverse, not −12,00 € (D12).
     */
    @Test
    void cal12_cancellation_uses_the_origin_version() {
        plantAndRun("CAL-12");
        search().body("amount", hasItems(15.00f, -15.00f));
    }

    /**
     * INT-17 (parcours): the radiation/reinstatement scenario plants a payable 100 € line in each of two
     * months; the date-effective exclusion and payment are pinned by PaymentServiceTest (REF-08).
     */
    @Test
    void int17_radiation_then_reinstatement_scenario_is_seeded() {
        given().when().post("/api/test/cr-scenario?cr=INT-17&entity=UNEP&month=2026-01-01").then().statusCode(204);
        search().body("findAll { it.nature == 'LINEAR' }.amount", hasItem(100.00f));
    }

    /**
     * INT-20 (parcours): a replacement order effective on the period's first day reassigns the contract's
     * main coverage from {@code PC-INT20} to {@code PC-INT20-B} when the calculation runs (REF-09). The
     * guarantee fiche then carries both affectations — the origin one closed on 2025-12-31, the new one
     * from 2026-01-01 with origin {@code REPLACEMENT_ORDER}.
     */
    @Test
    void int20_replacement_order_reassigns_the_coverage() {
        plantAndRun("INT-20");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/contracts/UNEP/INT20/coverages/INT20-C1")
                .then().statusCode(200)
                .body("assignments.productionCode", hasItems("PC-INT20", "PC-INT20-B"))
                .body("assignments.find { it.productionCode == 'PC-INT20' }.validTo", is("2025-12-31"))
                .body("assignments.find { it.productionCode == 'PC-INT20-B' }.validFrom", is("2026-01-01"))
                .body("assignments.find { it.productionCode == 'PC-INT20-B' }.origin", is("REPLACEMENT_ORDER"));
    }

    /**
     * INT-22 (parcours): an event referencing the contract by an unknown source-system code is held, not
     * rejected (REF-04). A calculation run before the code is mapped leaves it held (no line); once the
     * correspondence to the internal contract is added, the next run releases it and produces its line.
     */
    @Test
    void int22_mapping_an_external_code_releases_the_held_event() {
        given().when().post("/api/test/cr-scenario?cr=INT-22&entity=UNEP&month=2026-01-01").then().statusCode(204);
        // Before the mapping, the calculation cannot resolve the reference: the event stays held, no line.
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/run").then().statusCode(200);
        search().body("size()", is(0));
        // Map the source system's contract code to the internal contract (REF-04).
        given().auth().preemptive().basic("admin-unep", "admin-unep").contentType("application/json")
                .body("{\"entityCode\":\"UNEP\",\"sourceSystem\":\"SYS\",\"externalCode\":\"EXT-INT22\","
                        + "\"internalContractNumber\":\"INT22\"}")
                .when().post("/api/ui/v1/external-codes")
                .then().statusCode(200).body("objectType", is("CONTRACT")).body("internalCode", is("INT22"));
        // The next run releases the event and produces its 15,00 € line (100,00 € × 0,15).
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/run").then().statusCode(200);
        search().body("findAll { it.nature == 'LINEAR' }.amount", hasItem(15.00f));
    }

    /**
     * INT-22 (access and validation): mapping an external contract code refuses a blank field, an unknown
     * internal contract, a duplicate correspondence and an entity out of the user's scope.
     */
    @Test
    void int22_mapping_an_external_code_validates_its_input() {
        given().when().post("/api/test/cr-scenario?cr=INT-22&entity=UNEP&month=2026-01-01").then().statusCode(204);
        // A missing (null) field and a blank field are both refused (400), covering each guard leg.
        mapBody("{\"entityCode\":\"UNEP\",\"externalCode\":\"EXT-INT22\",\"internalContractNumber\":\"INT22\"}")
                .statusCode(400);
        mapContract("UNEP", "SYS", "", "INT22").statusCode(400);
        mapContract("UNEP", "SYS", "EXT-INT22", "").statusCode(400);
        // An unknown internal contract is refused (400).
        mapContract("UNEP", "SYS", "EXT-INT22", "NOPE").statusCode(400);
        // An unknown entity and an entity out of scope are refused (404).
        mapContract("NOPE", "SYS", "EXT-INT22", "INT22").statusCode(404);
        mapContract("ALPTIS_ASSURANCES", "SYS", "EXT-INT22", "INT22").statusCode(404);
        // The first valid mapping succeeds; a duplicate on the same source and code is refused (400).
        mapContract("UNEP", "SYS", "EXT-INT22", "INT22").statusCode(200);
        mapContract("UNEP", "SYS", "EXT-INT22", "INT22").statusCode(400);
    }

    /**
     * Posts an external-code mapping as the UNEP business administrator.
     *
     * @param entityCode     the entity code
     * @param sourceSystem   the source system
     * @param externalCode   the external code
     * @param internalNumber the internal contract number
     * @return the validatable response
     */
    private static io.restassured.response.ValidatableResponse mapContract(String entityCode, String sourceSystem,
            String externalCode, String internalNumber) {
        return mapBody("{\"entityCode\":\"" + entityCode + "\",\"sourceSystem\":\"" + sourceSystem + "\","
                + "\"externalCode\":\"" + externalCode + "\",\"internalContractNumber\":\"" + internalNumber + "\"}");
    }

    /**
     * Posts a raw external-code mapping body as the UNEP business administrator.
     *
     * @param body the JSON request body
     * @return the validatable response
     */
    private static io.restassured.response.ValidatableResponse mapBody(String body) {
        return given().auth().preemptive().basic("admin-unep", "admin-unep").contentType("application/json")
                .body(body).when().post("/api/ui/v1/external-codes").then();
    }
}
