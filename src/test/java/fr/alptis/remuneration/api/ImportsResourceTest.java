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
 * End-to-end HTTP tests for {@link ImportsResource}: upload with counters, journal, rejection
 * resolution, reimport refusal (CR-68), per-role access and entity partition (CR-70).
 */
@QuarkusTest
class ImportsResourceTest {

    @Inject
    TestDatabaseReset reset;

    @BeforeEach
    void resetBaseline() {
        reset.reset();
    }

    private static final String DISTRIBUTORS =
            "entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n"
                    + "ALPTIS_ASSURANCES;SESAME;P1;Cabinet;INDEPENDENT;ACTIVE;2026-01-01\n"
                    + "ALPTIS_ASSURANCES;SESAME;;Sans code;INDEPENDENT;ACTIVE;2026-01-01\n";

    private static String uploadBody(String fileName, String content) {
        return "{\"entityCode\":\"ALPTIS_ASSURANCES\",\"flowCode\":\"distributors\","
                + "\"sourceSystem\":\"SESAME\",\"fileName\":\"" + fileName + "\",\"content\":\""
                + content.replace("\n", "\\n") + "\"}";
    }

    /**
     * An operator uploads a file; the journal shows the five counters.
     */
    @Test
    void operator_uploads_and_reads_the_journal() {
        int batchId = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body(uploadBody("dist.csv", DISTRIBUTORS))
                .when().post("/api/ui/v1/imports")
                .then().statusCode(201)
                .body("linesRead", is(2)).body("linesLoaded", is(1)).body("linesRejected", is(1))
                .extract().jsonPath().getInt("batchId");

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/imports")
                .then().statusCode(200)
                .body("items.flowCode", hasItem("distributors"));

        int rejectionId = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/imports/" + batchId + "/rejections")
                .then().statusCode(200)
                .body("reasonCode", hasItem("MISSING_FIELD"))
                .extract().jsonPath().getInt("[0].id");

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/imports/rejections/" + rejectionId + "/correct")
                .then().statusCode(204);

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/imports/" + batchId + "/rejections")
                .then().statusCode(200)
                .body("[0].status", is("CORRECTED"));
    }

    /**
     * CR-68: the same file uploaded twice is refused.
     */
    @Test
    void cr68_reimport_is_refused() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body(uploadBody("same.csv", DISTRIBUTORS))
                .when().post("/api/ui/v1/imports").then().statusCode(201);

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body(uploadBody("same.csv", DISTRIBUTORS))
                .when().post("/api/ui/v1/imports").then().statusCode(409)
                .contentType("application/problem+json");
    }

    /**
     * CR-70: a UNEP operator cannot upload into Alptis.
     */
    @Test
    void cr70_cannot_upload_for_another_entity() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .contentType(ContentType.JSON).body(uploadBody("x.csv", DISTRIBUTORS))
                .when().post("/api/ui/v1/imports").then().statusCode(404);
    }

    /**
     * An events file referencing an unknown contract is held; the held lines are listed, and a
     * rejected line can be discarded.
     */
    @Test
    void lists_held_events_and_discards_a_rejection() {
        String events = "entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n"
                + "ALPTIS_ASSURANCES;CLEVA;E-1;PREMIUM_COLLECTED;AD-UNKNOWN;C1;2020-01-05;100.00\n";
        int heldBatch = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"flowCode\":\"events\",\"sourceSystem\":\"CLEVA\","
                        + "\"fileName\":\"ev.csv\",\"content\":\"" + events.replace("\n", "\\n") + "\"}")
                .when().post("/api/ui/v1/imports")
                .then().statusCode(201).body("linesOnHold", is(1))
                .extract().jsonPath().getInt("batchId");

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/imports/" + heldBatch + "/held")
                .then().statusCode(200).body("sourceKey", hasItem("E-1"));

        int batchId = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body(uploadBody("disc.csv", DISTRIBUTORS))
                .when().post("/api/ui/v1/imports").then().statusCode(201)
                .extract().jsonPath().getInt("batchId");
        int rejectionId = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/imports/" + batchId + "/rejections")
                .then().statusCode(200).extract().jsonPath().getInt("[0].id");
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/imports/rejections/" + rejectionId + "/discard")
                .then().statusCode(204);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/imports/" + batchId + "/rejections")
                .then().statusCode(200).body("[0].status", is("DISCARDED"));
    }

    /**
     * A viewer may not upload.
     */
    @Test
    void viewer_cannot_upload() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON).body(uploadBody("v.csv", DISTRIBUTORS))
                .when().post("/api/ui/v1/imports").then().statusCode(403);
    }
}
