package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

/**
 * End-to-end HTTP tests for {@link AuditResource}: a manual action appears in the trail, the trail
 * is partitioned by entity (CR-70) and read-only to the allowed roles, with pagination.
 */
@QuarkusTest
class AuditResourceTest {

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

    private static void updateAlptisPayment(String amount) {
        int version = given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(200).extract().jsonPath().getInt("version");
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .header("X-Version", version)
                .contentType("application/json")
                .body("{\"minPaymentAmount\":" + amount + ",\"advanceSettlementDays\":360,"
                        + "\"advanceSettlementTolerance\":1.00,\"paymentCalendar\":\"{}\"}")
                .when().put("/api/ui/v1/entities/ALPTIS_ASSURANCES")
                .then().statusCode(200);
    }

    /**
     * A manual update appears in the audit trail with its author and action.
     */
    @Test
    void manual_action_appears_in_the_trail() {
        updateAlptisPayment("25.00");

        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/audit")
                .then().statusCode(200)
                .body("items.action", hasItem("UPDATE_ENTITY_PARAMETERS"))
                .body("items.userId", hasItem("admin-alptis"))
                .body("items.entityCode", hasItem("ALPTIS_ASSURANCES"));
    }

    /**
     * CR-70: a UNEP user does not see Alptis audit traces.
     */
    @Test
    void cr70_audit_is_partitioned_by_entity() {
        updateAlptisPayment("25.00");

        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/audit")
                .then().statusCode(200)
                .body("items.entityCode", not(hasItem("ALPTIS_ASSURANCES")));
    }

    /**
     * A non-positive page size falls back to the default size.
     */
    @Test
    void non_positive_size_uses_default() {
        given().auth().preemptive().basic("auditor", "auditor")
                .queryParam("size", 0)
                .when().get("/api/ui/v1/audit")
                .then().statusCode(200)
                .body("size", org.hamcrest.Matchers.is(20));
    }

    /**
     * A viewer may not read the audit trail.
     */
    @Test
    void viewer_cannot_read_audit() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/audit")
                .then().statusCode(403);
    }

    /**
     * Pagination caps the number of items returned on a page.
     */
    @Test
    void pagination_limits_the_page() {
        updateAlptisPayment("25.00");
        given().auth().preemptive().basic("it-admin", "it-admin")
                .contentType("application/json")
                .body("{\"directoryGroup\":\"grp-global\",\"role\":\"AUDITOR\",\"entityCode\":null}")
                .when().post("/api/ui/v1/role-mappings")
                .then().statusCode(201);

        given().auth().preemptive().basic("auditor", "auditor")
                .queryParam("size", 1)
                .when().get("/api/ui/v1/audit")
                .then().statusCode(200)
                .body("size", org.hamcrest.Matchers.is(1))
                .body("items.size()", lessThanOrEqualTo(1))
                .body("total", org.hamcrest.Matchers.greaterThanOrEqualTo(2));
    }
}
