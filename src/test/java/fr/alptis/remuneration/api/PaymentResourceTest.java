package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for {@link PaymentResource}: the lots of a period, lot detail, preparation, Finance
 * validation with SEPA, the bank result and the off-cycle lot, with the entity partition and access.
 */
@QuarkusTest
class PaymentResourceTest {

    @Inject
    TestDatabaseReset reset;
    @Inject
    CycleDemoSeeder seeder;

    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds the cycle scene (with a prepared lot and statements). */
    @BeforeEach
    void seed() {
        reset.reset();
        seeder.seed("ALPTIS_ASSURANCES", java.time.LocalDate.parse(MONTH));
    }

    /** Returns the cycle lot id. */
    private int cycleLotId() {
        return given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/payment/ALPTIS_ASSURANCES/" + MONTH + "/lots")
                .then().statusCode(200).body("batchType", hasItem("CYCLE"))
                .extract().path("find { it.batchType == 'CYCLE' }.id");
    }

    /**
     * The lot is prepared, validated by Finance with a SEPA file, and its order settled by the bank.
     */
    @Test
    void prepare_validate_and_settle() {
        int lot = cycleLotId();
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/payment/lots/" + lot + "/prepare")
                .then().statusCode(200).body("orders[0].iban", is("FR7630001007941234567890185"));

        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().post("/api/ui/v1/payment/lots/" + lot + "/validate")
                .then().statusCode(200).body("orders[0].status", is("ISSUED"));

        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/payment/ALPTIS_ASSURANCES/" + MONTH + "/lots")
                .then().statusCode(200).body("find { it.batchType == 'CYCLE' }.status", is("ISSUED"));

        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/payment/lots/" + lot + "/sepa")
                .then().statusCode(200).body(org.hamcrest.Matchers.containsString("pain.001.001.03"));

        int order = given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/payment/lots/" + lot)
                .then().statusCode(200).extract().path("orders[0].id");
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .contentType(ContentType.JSON).body("{\"settled\":true}")
                .when().post("/api/ui/v1/payment/orders/" + order + "/result")
                .then().statusCode(200).body("status", is("SETTLED"));
    }

    /**
     * The lot detail lists its orders and its exclusion (the debtor account).
     */
    @Test
    void lot_detail_lists_orders_and_exclusions() {
        int lot = cycleLotId();
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/payment/lots/" + lot)
                .then().statusCode(200)
                .body("exclusions.reason", hasItem("NON_POSITIVE_BALANCE"));
    }

    /**
     * An off-cycle lot is created for a distributor.
     */
    @Test
    void creates_off_cycle_lot() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/payment/ALPTIS_ASSURANCES/" + MONTH + "/off-cycle/P13897")
                .then().statusCode(200).body("batchType", is("OFF_CYCLE"));
    }

    /**
     * Unknown lots, orders, entities and empty periods are all reported as not found.
     */
    @Test
    void not_found_paths() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/payment/lots/999999").then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/payment/lots/999999/prepare").then().statusCode(404);
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .contentType(ContentType.JSON).body("{\"settled\":true}")
                .when().post("/api/ui/v1/payment/orders/999999/result").then().statusCode(404);
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/payment/GHOST/" + MONTH + "/lots").then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/payment/ALPTIS_ASSURANCES/2099-01-01/lots").then().statusCode(404);
    }

    /**
     * A lot of another entity is out of scope and an operator may not validate for Finance.
     */
    @Test
    void scope_and_role() {
        int lot = cycleLotId();
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/payment/lots/" + lot + "/validate")
                .then().statusCode(403);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/payment/UNEP/" + MONTH + "/lots")
                .then().statusCode(404);
    }

    /** Returns the seeded cheque lot id of the Alptis period. */
    private int chequeLotId() {
        return given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/payment/ALPTIS_ASSURANCES/" + MONTH + "/lots")
                .then().statusCode(200).body("batchType", hasItem("CHEQUE"))
                .extract().path("find { it.batchType == 'CHEQUE' }.id");
    }

    /**
     * The Finance controller records the emission of the seeded cheque lot (PAY-04); a viewer may not.
     */
    @Test
    void finance_records_cheque_emission() {
        int chequeId = chequeLotId();
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().post("/api/ui/v1/payment/lots/" + chequeId + "/cheque-emission")
                .then().statusCode(200).body("status", is("SETTLED"));

        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().post("/api/ui/v1/payment/lots/" + chequeId + "/cheque-emission")
                .then().statusCode(403);
    }

    /**
     * The Finance controller downloads the cheque list of the seeded cheque lot (PAI-05).
     */
    @Test
    void finance_downloads_the_cheque_list() {
        int chequeId = chequeLotId();
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/payment/lots/" + chequeId + "/cheque-list")
                .then().statusCode(200).body(org.hamcrest.Matchers.containsString("beneficiaire;montant;reference_cheque"));
    }

    /**
     * A Finance controller of another entity cannot record an Alptis cheque emission.
     */
    @Test
    void cheque_emission_out_of_scope_is_not_found() {
        int chequeId = chequeLotId();
        given().auth().preemptive().basic("finance-unep", "finance-unep")
                .when().post("/api/ui/v1/payment/lots/" + chequeId + "/cheque-emission")
                .then().statusCode(404);
    }
}
