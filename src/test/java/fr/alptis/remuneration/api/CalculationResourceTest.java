package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * End-to-end HTTP tests for {@link CalculationResource}: launching a full run and a partial recalc,
 * reading the executions, the entity partition and per-role access (CAL-01, CAL-12).
 */
@QuarkusTest
class CalculationResourceTest {

    @Inject
    TestDatabaseReset reset;

    @BeforeEach
    void resetBaseline() {
        reset.reset();
    }

    /**
     * An operator launches a full run, reads the executions and launches a partial recalc.
     */
    @Test
    void operator_runs_recalcs_and_reads_executions() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/calculations/ALPTIS_ASSURANCES/2026-09-01/run")
                .then().statusCode(200)
                .body("runs.size()", greaterThanOrEqualTo(1));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/calculations/ALPTIS_ASSURANCES/2026-09-01")
                .then().statusCode(200)
                .body("runs.size()", greaterThanOrEqualTo(1));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .queryParam("type", "PRODUCT").queryParam("value", "DEMO_SANTE")
                .when().post("/api/ui/v1/calculations/ALPTIS_ASSURANCES/2026-09-01/recalc")
                .then().statusCode(200)
                .body("runs.size()", greaterThanOrEqualTo(2));
    }

    /**
     * A UNEP operator cannot read or launch an Alptis calculation.
     */
    @Test
    void cannot_reach_another_entity() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/calculations/ALPTIS_ASSURANCES/2026-09-01")
                .then().statusCode(404);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/ALPTIS_ASSURANCES/2026-09-01/run")
                .then().statusCode(404);
    }

    /**
     * A Finance controller may read the executions but not launch a run; a viewer may not read it.
     */
    @Test
    void finance_reads_but_cannot_launch_viewer_is_excluded() {
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/calculations/ALPTIS_ASSURANCES/2026-09-01")
                .then().statusCode(200);
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().post("/api/ui/v1/calculations/ALPTIS_ASSURANCES/2026-09-01/run")
                .then().statusCode(403);
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/calculations/ALPTIS_ASSURANCES/2026-09-01")
                .then().statusCode(403);
    }
}
