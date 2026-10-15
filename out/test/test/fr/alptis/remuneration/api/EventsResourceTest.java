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

    /**
     * Builds a manual complementary-data (coverage) request body for the given entity.
     *
     * @param entity the entity code
     * @return the JSON body
     */
    private static String coverageBody(String entity) {
        return "{\"entityCode\":\"" + entity + "\",\"sourceSystem\":\"MANUAL\",\"fields\":{"
                + "\"contract_number\":\"AD-M-1\",\"coverage_code\":\"GAR-M-1\",\"product_code\":\"P-X\","
                + "\"policyholder_id\":\"SUB-1\",\"production_code\":\"PC-X\",\"remuneration_mode\":\"LINEAR\","
                + "\"status\":\"IN_FORCE\",\"registration_date\":\"2026-01-15\",\"effect_date\":\"2026-02-01\","
                + "\"annual_premium\":\"1200.00\"}}";
    }

    /**
     * An operator enters a well-formed complementary datum referencing an unknown product: it is held
     * (ING-07), and journalled.
     */
    @Test
    void operator_enters_complementary_data_held_for_unknown_reference() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body(coverageBody("ALPTIS_ASSURANCES"))
                .when().post("/api/ui/v1/events/complementary-data")
                .then().statusCode(201)
                .body("loaded", is(0)).body("onHold", is(1)).body("rejected", is(0));
    }

    /**
     * A UNEP operator cannot enter complementary data into Alptis: the entity is out of scope.
     */
    @Test
    void cannot_enter_complementary_data_for_another_entity() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .contentType(ContentType.JSON).body(coverageBody("ALPTIS_ASSURANCES"))
                .when().post("/api/ui/v1/events/complementary-data")
                .then().statusCode(404)
                .contentType("application/problem+json");
    }

    /**
     * A viewer may not enter complementary data.
     */
    @Test
    void viewer_cannot_enter_complementary_data() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON).body(coverageBody("ALPTIS_ASSURANCES"))
                .when().post("/api/ui/v1/events/complementary-data")
                .then().statusCode(403);
    }

    /**
     * An operator searches events, reads the held-by-reason counts and opens an event's detail
     * (ING-06): a manually-entered, held event is found, searchable, and shows its hold reason.
     */
    @Test
    void operator_searches_and_opens_an_event() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body(body("ALPTIS_ASSURANCES", "SEARCH-1",
                        ",\"contract_number\":\"C-X\",\"event_date\":\"2026-09-30\",\"gross_amount\":\"120.00\""))
                .when().post("/api/ui/v1/events").then().statusCode(201);

        int id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .queryParam("q", "SEARCH-1").queryParam("status", "ON_HOLD").queryParam("type", "PREMIUM_COLLECTED")
                .when().get("/api/ui/v1/events")
                .then().statusCode(200)
                .body("items.sourceKey", hasItem("SEARCH-1"))
                .extract().jsonPath().getInt("items[0].id");

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/events/held-by-reason")
                .then().statusCode(200)
                .body("reason", hasItem("UNKNOWN_REFERENCE"));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/events/" + id)
                .then().statusCode(200)
                .body("event.sourceKey", is("SEARCH-1"))
                .body("event.holdReason", is("UNKNOWN_REFERENCE"));
    }

    /**
     * A sales representative may not search events (outside the imports-screen roles).
     */
    @Test
    void sales_rep_cannot_search_events() {
        given().auth().preemptive().basic("rep-alptis", "rep-alptis")
                .when().get("/api/ui/v1/events")
                .then().statusCode(403);
    }
}
