package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link MeResource} end to end over HTTP Basic, exercising the whole security chain
 * (identity provider, role resolver, seeded mappings).
 */
@QuarkusTest
class MeResourceTest {

    /**
     * Verifies that an entity-scoped user sees its own role and entity.
     */
    @Test
    void returns_the_authenticated_scoped_user() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/me")
                .then().statusCode(200)
                .body("username", is("operator-alptis"))
                .body("roles", hasItem("OPERATOR"))
                .body("entities", hasItem("ALPTIS_ASSURANCES"));
    }

    /**
     * Verifies that a group-level user sees every entity.
     */
    @Test
    void returns_all_entities_for_a_group_level_user() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .when().get("/api/ui/v1/me")
                .then().statusCode(200)
                .body("roles", hasItem("IT_ADMIN"))
                .body("entities", hasItem("*"));
    }

    /**
     * Verifies that the endpoint requires authentication.
     */
    @Test
    void requires_authentication() {
        when().get("/api/ui/v1/me")
                .then().statusCode(401);
    }

    /**
     * Verifies that a wrong password is rejected.
     */
    @Test
    void rejects_a_wrong_password() {
        given().auth().preemptive().basic("operator-alptis", "wrong")
                .when().get("/api/ui/v1/me")
                .then().statusCode(401);
    }
}
