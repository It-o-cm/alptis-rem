package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for {@link CycleResource}: the open-period list (WKF-04), a period's detail, its state
 * transition by role, and the entity partition and access control.
 */
@QuarkusTest
class CycleResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds one open Alptis period before each test. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
            Period period = new Period();
            period.entity = alptis;
            period.periodMonth = LocalDate.parse(MONTH);
            period.state = PeriodState.OPEN;
            period.persist();
            // A period of another entity, filtered out of a restricted operator's list.
            LegalEntity unep = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
            Period other = new Period();
            other.entity = unep;
            other.periodMonth = LocalDate.parse(MONTH);
            other.state = PeriodState.OPEN;
            other.persist();
        });
    }

    /**
     * The operator sees the open period of its entity in the advancement list.
     */
    @Test
    void lists_open_periods() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/periods")
                .then().statusCode(200)
                .body("entityCode", hasItem("ALPTIS_ASSURANCES"));
    }

    /**
     * The operator reads the detail of its entity's period.
     */
    @Test
    void reads_period_detail() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/periods/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(200)
                .body("period.state", is("OPEN"));
    }

    /**
     * The operator moves the period from open to loaded.
     */
    @Test
    void transitions_period() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body("{\"toState\":\"LOADED\"}")
                .when().post("/api/ui/v1/periods/ALPTIS_ASSURANCES/" + MONTH + "/transition")
                .then().statusCode(200)
                .body("state", is("LOADED"));
    }

    /**
     * A period out of the caller's entity partition is not found.
     */
    @Test
    void period_out_of_scope_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/periods/UNEP/" + MONTH)
                .then().statusCode(404);
    }

    /**
     * An unknown period is not found.
     */
    @Test
    void unknown_period_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/periods/ALPTIS_ASSURANCES/2020-01-01")
                .then().statusCode(404);
    }

    /**
     * A viewer may not drive a transition.
     */
    @Test
    void viewer_cannot_transition() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON).body("{\"toState\":\"LOADED\"}")
                .when().post("/api/ui/v1/periods/ALPTIS_ASSURANCES/" + MONTH + "/transition")
                .then().statusCode(403);
    }

    /**
     * After a transition, the period detail lists the recorded transition.
     */
    @Test
    void detail_lists_transitions_after_advance() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body("{\"toState\":\"LOADED\"}")
                .when().post("/api/ui/v1/periods/ALPTIS_ASSURANCES/" + MONTH + "/transition")
                .then().statusCode(200);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/periods/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(200)
                .body("transitions[0].toState", is("LOADED"));
    }

    /**
     * An unrestricted auditor lists every open period and gets a not found on an unknown one.
     */
    @Test
    void auditor_sees_all_and_unknown_is_not_found() {
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/periods")
                .then().statusCode(200)
                .body("entityCode", hasItem("UNEP"));
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/periods/GHOST/" + MONTH)
                .then().statusCode(404);
    }
}
