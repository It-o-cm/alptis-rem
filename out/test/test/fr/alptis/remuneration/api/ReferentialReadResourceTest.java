package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * HTTP tests for the referential consultation resources: products (REF-01), quality indicators
 * (REF-07), bank accounts (REF-05), transfers and replacement orders (REF-09), payment rules
 * (REF-06), external codes (REF-04), distributor and contract search (REF-02, 5.2). Each endpoint is
 * reached so its delegation is covered; data content is asserted in the service and E2E tests.
 */
@QuarkusTest
class ReferentialReadResourceTest {

    @Inject
    TestDatabaseReset reset;

    /** Restores the baseline before each test. */
    @BeforeEach
    void resetBaseline() {
        reset.reset();
    }

    /** Performs an authorised GET and expects 200. */
    private static void get200(String path) {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get(path).then().statusCode(200);
    }

    /**
     * Every referential list endpoint answers for an authorised operator.
     */
    @Test
    void lists_every_referential() {
        get200("/api/ui/v1/products");
        get200("/api/ui/v1/insurers");
        get200("/api/ui/v1/ranges");
        get200("/api/ui/v1/options");
        get200("/api/ui/v1/quality-indicators");
        get200("/api/ui/v1/bank-accounts");
        get200("/api/ui/v1/portfolio-transfers");
        get200("/api/ui/v1/replacement-orders");
        get200("/api/ui/v1/payee-rules");
        get200("/api/ui/v1/external-codes");
        get200("/api/ui/v1/groups");
        get200("/api/ui/v1/delegations");
        get200("/api/ui/v1/production-codes");
        get200("/api/ui/v1/distributors?q=x");
        get200("/api/ui/v1/contracts?q=x");
    }

    /**
     * After importing a grouping, its detail is returned with its members (CPT-02, REF-03).
     */
    @Test
    void returns_a_grouping_detail() {
        String groups = "group_code;label;head_partner_code\nGRP-TEST;Groupement Test;\n";
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(io.restassured.http.ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"flowCode\":\"distributor_groups\",\"sourceSystem\":\"SESAME\","
                        + "\"fileName\":\"g.csv\",\"content\":\"" + groups.replace("\n", "\\n") + "\"}")
                .when().post("/api/ui/v1/imports").then().statusCode(201);

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/groups/GRP-TEST")
                .then().statusCode(200).body("group.code", org.hamcrest.Matchers.is("GRP-TEST"));
    }

    /**
     * An unknown grouping detail is reported as not found (CPT-02).
     */
    @Test
    void unknown_group_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/groups/NOPE")
                .then().statusCode(404);
    }

    /**
     * A distributor detail for an unknown code is reported as not found.
     */
    @Test
    void unknown_distributor_detail_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/distributors/UNKNOWN")
                .then().statusCode(404);
    }

    /**
     * After importing a distributor, its detail is returned.
     */
    @Test
    void returns_a_seeded_distributor_detail() {
        String dist = "entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n"
                + "ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n";
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(io.restassured.http.ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"flowCode\":\"distributors\",\"sourceSystem\":\"SESAME\","
                        + "\"fileName\":\"d.csv\",\"content\":\"" + dist.replace("\n", "\\n") + "\"}")
                .when().post("/api/ui/v1/imports").then().statusCode(201);

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/distributors/P13897")
                .then().statusCode(200).body("partnerCode", org.hamcrest.Matchers.is("P13897"));
    }
}
