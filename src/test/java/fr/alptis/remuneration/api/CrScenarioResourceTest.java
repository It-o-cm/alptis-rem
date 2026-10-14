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

    /** Plants a case and runs the real calculation of the seeded UNEP month. */
    private static void plantAndRun(String crCode) {
        given().when().post("/api/test/cr-scenario?cr=" + crCode + "&entity=UNEP&month=2026-01-01")
                .then().statusCode(204);
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/calculations/UNEP/2026-01-01/run").then().statusCode(200);
    }

    /** The reporting search of the seeded UNEP month, ready for body assertions. */
    private static io.restassured.response.ValidatableResponse search() {
        return given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/reporting/UNEP/search?month=2026-01-01").then().statusCode(200);
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
}
