package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * Verifies the dev-only recette-case loader plants a case's data and that the real engine, run from the
 * calculation endpoint over the seeded period, produces the expected line — and that an unknown case is
 * refused.
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

    /**
     * CR-01 planted and calculated through the API yields one LINEAR line of 15,00 € on the search; an
     * unknown case code is refused with 400.
     */
    @Test
    void plants_cr01_and_the_engine_computes_the_linear_line() {
        given().when().post("/api/test/cr-scenario?cr=CR-01&entity=UNEP&month=2026-01-01")
                .then().statusCode(204);
        given().when().post("/api/test/cr-scenario?cr=CR-99&entity=UNEP&month=2026-01-01")
                .then().statusCode(400);

        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/run")
                .then().statusCode(200);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/reporting/UNEP/search?month=2026-01-01")
                .then().statusCode(200)
                .body("nature", hasItem("LINEAR"))
                .body("find { it.nature == 'LINEAR' }.amount", is(15.00f));
    }
}
