package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * Verifies the dev-only sales-force demo seeder plants a collaborator visible on the FDV endpoints.
 */
@QuarkusTest
class FdvDemoResourceTest {

    @Inject
    TestDatabaseReset reset;

    /** Resets the database before each test. */
    @BeforeEach
    void setUp() {
        reset.reset();
    }

    /**
     * Seeding the scene makes the demo collaborator visible to the business administrator.
     */
    @Test
    void seeds_a_sales_force_scene() {
        given().when().post("/api/test/fdv-demo?entity=ALPTIS_ASSURANCES&month=2026-09-01")
                .then().statusCode(204);
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/fdv/ALPTIS_ASSURANCES/collaborators")
                .then().statusCode(200).body("externalId", hasItem("C-DEMO"));
    }

    /**
     * FDV-03: the monthly individual variable pays the post's instalment only when the objective is met
     * and the absence stays under the ten-day threshold — nothing when the objective is missed, nothing
     * when ten working days are absent.
     */
    @Test
    void fdv03_monthly_pays_only_when_the_objective_is_met_without_absence() {
        given().when().post("/api/test/fdv-demo/fdv03?entity=ALPTIS_ASSURANCES&month=2026-11-01")
                .then().statusCode(204);
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().post("/api/ui/v1/fdv/ALPTIS_ASSURANCES/2026-11-01/run")
                .then().statusCode(200)
                .body("find { it.employee == 'FDV03-OK' }.amount as double", is(146.0))
                .body("find { it.employee == 'FDV03-MISS' }.amount as double", is(0.0))
                .body("find { it.employee == 'FDV03-ABS' }.amount as double", is(0.0));
    }

    /**
     * FDV-07: the annual regularization pays the positive gap (annual right over the monthly instalments
     * paid) and books the negative gap as a deficit submitted to the manager (H-53): +600 and −300.
     */
    @Test
    void fdv07_annual_regularization_books_positive_and_negative_gaps() {
        // The main sales-force demo plants the January N+1 period (and a CAC post), so the FDV-07 seed
        // reuses that period and the regularization also covers the demo collaborator.
        given().when().post("/api/test/fdv-demo?entity=ALPTIS_ASSURANCES&month=2026-09-01").then().statusCode(204);
        given().when().post("/api/test/fdv-demo/fdv07?entity=ALPTIS_ASSURANCES&year=2026").then().statusCode(204);
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().post("/api/ui/v1/fdv/ALPTIS_ASSURANCES/2026/annual-regularization")
                .then().statusCode(200).body("regularized", is(3));
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/fdv/ALPTIS_ASSURANCES/2027-01-01/results")
                .then().statusCode(200)
                .body("find { it.employee == 'FDV07-POS' }.amount as double", is(600.0))
                .body("find { it.employee == 'FDV07-NEG' }.amount as double", is(-300.0));
    }
}
