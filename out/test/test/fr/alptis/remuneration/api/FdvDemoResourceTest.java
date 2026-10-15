package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;

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
}
