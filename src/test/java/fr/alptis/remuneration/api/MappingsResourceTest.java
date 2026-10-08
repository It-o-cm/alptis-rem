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
 * End-to-end HTTP tests for {@link MappingsResource}: test a mapping on a sample (INT-13), add a
 * version, and per-role access.
 */
@QuarkusTest
class MappingsResourceTest {

    @Inject
    TestDatabaseReset reset;

    @BeforeEach
    void resetBaseline() {
        reset.reset();
    }

    private static final String DEFINITION =
            "{\\\"filter\\\":{\\\"column\\\":\\\"NOM_MUTUELLE\\\",\\\"equals\\\":\\\"ALPTIS\\\"},"
                    + "\\\"fields\\\":{\\\"source_key\\\":{\\\"column\\\":\\\"CODE_MOUVEMENT\\\"}}}";

    private static final String SAMPLE = "CODE_MOUVEMENT;NOM_MUTUELLE\\nM1;ALPTIS\\nM2;UMR\\n";

    /**
     * INT-13: a business admin tests a mapping on a sample and sees the pivot result and the
     * filtered count.
     */
    @Test
    void int13_tests_a_mapping_on_a_sample() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .contentType(ContentType.JSON)
                .body("{\"definition\":\"" + DEFINITION + "\",\"sample\":\"" + SAMPLE + "\"}")
                .when().post("/api/ui/v1/mappings/test")
                .then().statusCode(200)
                .body("filtered", is(1))
                .body("rows.source_key", hasItem("M1"));
    }

    /**
     * A business admin adds a mapping version, which then appears in the list.
     */
    @Test
    void admin_adds_a_mapping_version() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"flowCode\":\"events\",\"sourceSystem\":\"UMR\","
                        + "\"definition\":\"" + DEFINITION + "\"}")
                .when().post("/api/ui/v1/mappings")
                .then().statusCode(201).body("versionNo", is(1));

        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/mappings")
                .then().statusCode(200).body("flowCode", hasItem("events"));
    }

    /**
     * An operator may test a mapping but not add a version.
     */
    @Test
    void operator_cannot_add_a_version() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"flowCode\":\"events\",\"sourceSystem\":\"UMR\","
                        + "\"definition\":\"" + DEFINITION + "\"}")
                .when().post("/api/ui/v1/mappings")
                .then().statusCode(403);
    }

    /**
     * An unrestricted auditor can list the mappings of every entity.
     */
    @Test
    void auditor_lists_all_mappings() {
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/mappings")
                .then().statusCode(200);
    }

    /**
     * An admin cannot add a version for an entity outside its scope, nor for an unknown entity.
     */
    @Test
    void admin_cannot_add_a_version_out_of_scope_or_for_unknown_entity() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .contentType(ContentType.JSON)
                .body("{\"entityCode\":\"UNEP\",\"flowCode\":\"events\",\"sourceSystem\":\"UMR\","
                        + "\"definition\":\"" + DEFINITION + "\"}")
                .when().post("/api/ui/v1/mappings")
                .then().statusCode(404);

        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .contentType(ContentType.JSON)
                .body("{\"entityCode\":\"NOPE\",\"flowCode\":\"events\",\"sourceSystem\":\"UMR\","
                        + "\"definition\":\"" + DEFINITION + "\"}")
                .when().post("/api/ui/v1/mappings")
                .then().statusCode(404);
    }
}
