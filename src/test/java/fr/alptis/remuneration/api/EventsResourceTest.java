package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * End-to-end HTTP tests for {@link EventsResource}: manual event entry (ING-07) with the held and
 * rejected outcomes, the entity partition and per-role access.
 */
@QuarkusTest
class EventsResourceTest {

    @Inject
    TestDatabaseReset reset;

    /** Resets the database to its seeded baseline before each test. */
    @BeforeEach
    void resetBaseline() {
        reset.reset();
    }

    /**
     * Builds a manual event request body for the given entity and fields.
     *
     * @param entity the entity code
     * @param key    the source key
     * @param extra  extra JSON fields (contract, date, amount), already comma-prefixed
     * @return the JSON body
     */
    private static String body(String entity, String key, String extra) {
        return "{\"entityCode\":\"" + entity + "\",\"sourceSystem\":\"MANUAL\",\"fields\":{"
                + "\"source_key\":\"" + key + "\",\"event_type\":\"PREMIUM_COLLECTED\"" + extra + "}}";
    }

    /**
     * An operator enters a well-formed event referencing an unknown contract: it is held.
     */
    @Test
    void operator_enters_event_held_for_unknown_reference() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body(body("ALPTIS_ASSURANCES", "EVT-1",
                        ",\"contract_number\":\"C-X\",\"event_date\":\"2026-09-30\",\"gross_amount\":\"120.00\""))
                .when().post("/api/ui/v1/events")
                .then().statusCode(201)
                .body("loaded", is(0)).body("onHold", is(1)).body("rejected", is(0));
    }

    /**
     * An incomplete entry (no contract, no date) is rejected.
     */
    @Test
    void operator_incomplete_entry_is_rejected() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body(body("ALPTIS_ASSURANCES", "EVT-2", ",\"gross_amount\":\"50.00\""))
                .when().post("/api/ui/v1/events")
                .then().statusCode(201)
                .body("rejected", is(1));
    }

    /**
     * A UNEP operator cannot enter an event into Alptis: the entity is out of scope.
     */
    @Test
    void cannot_enter_event_for_another_entity() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .contentType(ContentType.JSON)
                .body(body("ALPTIS_ASSURANCES", "EVT-3",
                        ",\"contract_number\":\"C-X\",\"event_date\":\"2026-09-30\",\"gross_amount\":\"10.00\""))
                .when().post("/api/ui/v1/events")
                .then().statusCode(404)
                .contentType("application/problem+json");
    }

    /**
     * A viewer may not enter events.
     */
    @Test
    void viewer_cannot_enter_events() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON)
                .body(body("ALPTIS_ASSURANCES", "EVT-4", ",\"gross_amount\":\"10.00\""))
                .when().post("/api/ui/v1/events")
                .then().statusCode(403);
    }
}
