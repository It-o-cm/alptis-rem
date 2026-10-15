package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * End-to-end HTTP tests for {@link RoleMappingsResource}: per-role access and create/delete of the
 * directory-group to role correspondence (ADM-01). Each test starts from the seeded baseline.
 */
@QuarkusTest
class RoleMappingsResourceTest {

    /** Restores the baseline before each test. */
    @Inject
    TestDatabaseReset reset;

    /**
     * Resets the database to its seeded baseline.
     */
    @BeforeEach
    void resetBaseline() {
        reset.reset();
    }

    private static final String NEW_MAPPING =
            "{\"directoryGroup\":\"grp-test\",\"role\":\"VIEWER\",\"entityCode\":\"ALPTIS_ASSURANCES\"}";

    /**
     * The technical administrator lists the seeded mappings.
     */
    @Test
    void it_admin_lists_mappings() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .when().get("/api/ui/v1/role-mappings")
                .then().statusCode(200)
                .body("directoryGroup", hasItem("grp-op-alptis"));
    }

    /**
     * The auditor may read the mappings.
     */
    @Test
    void auditor_can_read_mappings() {
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/ui/v1/role-mappings")
                .then().statusCode(200);
    }

    /**
     * An operator may not read the mappings.
     */
    @Test
    void operator_cannot_read_mappings() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/role-mappings")
                .then().statusCode(403);
    }

    /**
     * The technical administrator creates then deletes a mapping.
     */
    @Test
    void create_then_delete_a_mapping() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .contentType("application/json").body(NEW_MAPPING)
                .when().post("/api/ui/v1/role-mappings")
                .then().statusCode(201)
                .body("directoryGroup", org.hamcrest.Matchers.is("grp-test"))
                .body("entityCode", org.hamcrest.Matchers.is("ALPTIS_ASSURANCES"));

        given().auth().preemptive().basic("it-admin", "it-admin")
                .when().get("/api/ui/v1/role-mappings")
                .then().statusCode(200)
                .body("directoryGroup", hasItem("grp-test"));

        given().auth().preemptive().basic("it-admin", "it-admin")
                .queryParam("group", "grp-test").queryParam("role", "VIEWER")
                .queryParam("entity", "ALPTIS_ASSURANCES")
                .when().delete("/api/ui/v1/role-mappings")
                .then().statusCode(204);

        given().auth().preemptive().basic("it-admin", "it-admin")
                .when().get("/api/ui/v1/role-mappings")
                .then().statusCode(200)
                .body("directoryGroup", not(hasItem("grp-test")));
    }

    /**
     * A mapping submitted without a role is rejected as invalid (HTTP 400), not crashed (no NPE).
     */
    @Test
    void a_mapping_without_a_role_is_rejected() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .contentType("application/json")
                .body("{\"directoryGroup\":\"grp-x\",\"entityCode\":\"ALPTIS_ASSURANCES\"}")
                .when().post("/api/ui/v1/role-mappings")
                .then().statusCode(400);
    }

    /**
     * A global mapping (no entity) is created with a null entity code.
     */
    @Test
    void create_a_global_mapping() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .contentType("application/json")
                .body("{\"directoryGroup\":\"grp-global\",\"role\":\"AUDITOR\",\"entityCode\":null}")
                .when().post("/api/ui/v1/role-mappings")
                .then().statusCode(201)
                .body("entityCode", org.hamcrest.Matchers.nullValue());
    }

    /**
     * A blank entity code is treated as the every-entity scope.
     */
    @Test
    void blank_entity_is_treated_as_global() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .contentType("application/json")
                .body("{\"directoryGroup\":\"grp-blank\",\"role\":\"AUDITOR\",\"entityCode\":\"\"}")
                .when().post("/api/ui/v1/role-mappings")
                .then().statusCode(201)
                .body("entityCode", org.hamcrest.Matchers.nullValue());
    }

    /**
     * Creating an existing mapping is a 409 conflict.
     */
    @Test
    void duplicate_mapping_returns_409() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .contentType("application/json")
                .body("{\"directoryGroup\":\"grp-op-alptis\",\"role\":\"OPERATOR\","
                        + "\"entityCode\":\"ALPTIS_ASSURANCES\"}")
                .when().post("/api/ui/v1/role-mappings")
                .then().statusCode(409)
                .contentType("application/problem+json");
    }

    /**
     * An unknown role is a 400 validation error.
     */
    @Test
    void unknown_role_returns_400() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .contentType("application/json")
                .body("{\"directoryGroup\":\"grp-x\",\"role\":\"NOT_A_ROLE\",\"entityCode\":null}")
                .when().post("/api/ui/v1/role-mappings")
                .then().statusCode(400)
                .contentType("application/problem+json");
    }

    /**
     * An unknown entity is a 404.
     */
    @Test
    void unknown_entity_returns_404() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .contentType("application/json")
                .body("{\"directoryGroup\":\"grp-x\",\"role\":\"VIEWER\",\"entityCode\":\"NOPE\"}")
                .when().post("/api/ui/v1/role-mappings")
                .then().statusCode(404);
    }

    /**
     * Deleting an unknown mapping is a 404.
     */
    @Test
    void delete_unknown_returns_404() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .queryParam("group", "nope").queryParam("role", "VIEWER")
                .when().delete("/api/ui/v1/role-mappings")
                .then().statusCode(404);
    }

    /**
     * An operator may not create a mapping.
     */
    @Test
    void operator_cannot_create() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType("application/json").body(NEW_MAPPING)
                .when().post("/api/ui/v1/role-mappings")
                .then().statusCode(403);
    }
}
