package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * End-to-end HTTP tests for {@link EntitiesResource}: entity partition (CR-70), per-role access,
 * optimistic locking and error mapping. Each test starts from the seeded baseline.
 */
@QuarkusTest
class EntitiesResourceTest {

    /** Restores the baseline before each test. */
    @Inject
    TestDatabaseReset reset;

    /**
     * Resets the database to its seeded baseline.
     */
    @BeforeEach
    void resetBaseline() {
        reset.reset();
    }

    /**
     * A business administrator sees only the entity it is attached to.
     */
    @Test
    void admin_sees_only_its_entity() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/entities")
                .then().statusCode(200)
                .body("code", hasItem("ALPTIS_ASSURANCES"))
                .body("code", not(hasItem("UNEP")));
    }

    /**
     * The auditor, attached to every entity, sees them all.
     */
    @Test
    void auditor_sees_every_entity() {
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/entities")
                .then().statusCode(200)
                .body("code", hasItem("ALPTIS_ASSURANCES"))
                .body("code", hasItem("UNEP"))
                .body("code", hasItem("PROMANTIS_CONSEIL"));
    }

    /**
     * CR-70: a UNEP user sees no Alptis data, by list or by direct address.
     */
    @Test
    void cr70_unep_user_cannot_see_alptis() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/entities")
                .then().statusCode(200)
                .body("code", hasItem("UNEP"))
                .body("code", not(hasItem("ALPTIS_ASSURANCES")));

        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(404)
                .contentType("application/problem+json")
                .body("detail", containsString("ALPTIS_ASSURANCES"));
    }

    /**
     * The technical administrator has no right on entity parameters (amounts).
     */
    @Test
    void it_admin_cannot_view_entity_parameters() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .when().get("/api/ui/v1/entities")
                .then().statusCode(403);
    }

    /**
     * A business administrator updates the parameters; the change persists.
     */
    @Test
    void admin_updates_parameters() {
        int version = given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(200).extract().jsonPath().getInt("version");

        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .header("X-Version", version)
                .contentType("application/json")
                .body("{\"minPaymentAmount\":25.00,\"advanceSettlementDays\":360,"
                        + "\"advanceSettlementTolerance\":1.00,\"paymentCalendar\":\"{}\","
                        + "\"adjustmentApprovalThreshold\":500.00}")
                .when().put("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(200)
                .body("minPaymentAmount", org.hamcrest.Matchers.is(25.00f))
                .body("adjustmentApprovalThreshold", org.hamcrest.Matchers.is(500.00f));

        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(200)
                .body("minPaymentAmount", org.hamcrest.Matchers.is(25.00f))
                .body("adjustmentApprovalThreshold", org.hamcrest.Matchers.is(500.00f));
    }

    /**
     * Updating with a null payment calendar is accepted (covers the null snapshot path).
     */
    @Test
    void admin_updates_with_null_calendar() {
        int version = given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(200).extract().jsonPath().getInt("version");

        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .header("X-Version", version)
                .contentType("application/json")
                .body("{\"minPaymentAmount\":15.00,\"advanceSettlementDays\":360,"
                        + "\"advanceSettlementTolerance\":1.00,\"paymentCalendar\":null}")
                .when().put("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(200)
                .body("paymentCalendar", org.hamcrest.Matchers.nullValue());
    }

    /**
     * A stale version is rejected with a 409 conflict.
     */
    @Test
    void update_rejected_for_stale_version() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .header("X-Version", 99)
                .contentType("application/json")
                .body("{\"minPaymentAmount\":25.00,\"advanceSettlementDays\":360,"
                        + "\"advanceSettlementTolerance\":1.00,\"paymentCalendar\":\"{}\"}")
                .when().put("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(409)
                .contentType("application/problem+json");
    }

    /**
     * A viewer may not update entity parameters.
     */
    @Test
    void viewer_cannot_update() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .header("X-Version", 0)
                .contentType("application/json")
                .body("{\"minPaymentAmount\":25.00,\"advanceSettlementDays\":360,"
                        + "\"advanceSettlementTolerance\":1.00,\"paymentCalendar\":\"{}\"}")
                .when().put("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(403);
    }

    /**
     * An unknown entity yields a 404.
     */
    @Test
    void unknown_entity_returns_404() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/entities/DOES_NOT_EXIST")
                .then().statusCode(404)
                .contentType("application/problem+json");
    }
}
