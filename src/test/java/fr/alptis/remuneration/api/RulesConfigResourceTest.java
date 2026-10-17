package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for the rule-configuration resources (PAR-01 to PAR-12): plans, rules, versions, their
 * approval by a second administrator, the annual duplication, the plausibility alert, the family
 * schemas, agreements, discount conventions and enrollments, with per-role access. The database is
 * reset first so the Alptis initial barème does not interfere.
 */
@QuarkusTest
class RulesConfigResourceTest {

    @Inject
    TestDatabaseReset reset;

    /** Resets the database to its seeded baseline before each test. */
    @BeforeEach
    void resetBaseline() {
        reset.reset();
    }

    /** Performs a request as the first Alptis administrator. */
    private static io.restassured.specification.RequestSpecification admin() {
        return given().auth().preemptive().basic("admin-alptis", "admin-alptis");
    }

    /** Performs a request as the second Alptis administrator. */
    private static io.restassured.specification.RequestSpecification admin2() {
        return given().auth().preemptive().basic("admin2-alptis", "admin2-alptis");
    }

    /**
     * Lists the families and serves a family schema.
     */
    @Test
    void lists_families_and_serves_a_schema() {
        admin().when().get("/api/ui/v1/families").then().statusCode(200).body("code", hasItem("UPFRONT"));
        admin().when().get("/api/ui/v1/families/UPFRONT/schema").then().statusCode(200).body("type", is("object"));
    }

    /**
     * Creates a plan, a rule and a version, approves it with a second administrator, then duplicates
     * the plan and reads the plausibility alert.
     */
    @Test
    void creates_approves_and_duplicates() {
        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"code\":\"P1\",\"label\":\"Plan 1\","
                        + "\"direction\":\"INBOUND\",\"vintage\":2026}")
                .when().post("/api/ui/v1/plans").then().statusCode(201).body("code", is("P1"));
        admin().when().get("/api/ui/v1/plans").then().statusCode(200).body("code", hasItem("P1"));

        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"code\":\"R1\",\"label\":\"Règle 1\",\"family\":\"LINEAR\"}")
                .when().post("/api/ui/v1/plans/P1/rules").then().statusCode(201);
        admin().when().get("/api/ui/v1/plans/P1/rules?entityCode=ALPTIS_ASSURANCES")
                .then().statusCode(200).body("code", hasItem("R1"));
        // The flat rule list (ADM-03) carries the rule with its plan.
        admin().when().get("/api/ui/v1/rules")
                .then().statusCode(200).body("code", hasItem("R1")).body("planCode", hasItem("P1"));

        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\","
                        + "\"versionAnchor\":\"EVENT_DATE\",\"scope\":\"{\\\"product\\\":[\\\"ZZZ\\\"]}\","
                        + "\"triggerEventTypes\":[\"PREMIUM_COLLECTED\"],\"basis\":\"COLLECTED_PREMIUM\","
                        + "\"outputNature\":\"LINEAR\",\"parameters\":\"{\\\"allowedRates\\\":[0.15]}\","
                        + "\"clawbackPolicy\":\"{\\\"mode\\\":\\\"PRORATA\\\"}\",\"paymentSchedule\":\"{\\\"months\\\":12}\","
                        + "\"conditions\":[{\"indicatorType\":\"UNPAID_RATE\",\"operator\":\"LT\","
                        + "\"threshold\":0.05,\"measurementPeriod\":\"MONTH\",\"onFailure\":\"DEFER\"}],"
                        + "\"priority\":10,\"exclusionGroup\":\"TEST\"}")
                .when().post("/api/ui/v1/plans/P1/rules/R1/versions").then().statusCode(201).body("status", is("DRAFT"))
                .body("clawbackPolicy", is("{\"mode\":\"PRORATA\"}"))
                .body("paymentSchedule", is("{\"months\":12}"))
                .body("conditions[0].onFailure", is("DEFER"));

        admin().when().get("/api/ui/v1/plans/P1/rules/R1/versions?entityCode=ALPTIS_ASSURANCES")
                .then().statusCode(200).body("versionNo", hasItem(1))
                .body("[0].conditions[0].indicatorType", is("UNPAID_RATE"));

        // Approved and within its 2026 validity window at the current date: effectively ACTIVE (PAR-04).
        admin2().when().post("/api/ui/v1/plans/P1/rules/R1/versions/1/approve?entityCode=ALPTIS_ASSURANCES")
                .then().statusCode(200).body("status", is("ACTIVE"));

        // PAR-11: the amendment is generated from the version and sent for signature (unsigned).
        admin().when().post("/api/ui/v1/plans/P1/rules/R1/versions/1/amendment?entityCode=ALPTIS_ASSURANCES")
                .then().statusCode(201).body("reference", is("ALPTIS_ASSURANCES_AVENANT_R1_v1"))
                .body("signatureStatus", is("NOT_SIGNED"));

        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"newCode\":\"P1_2027\",\"newVintage\":2027}")
                .when().post("/api/ui/v1/plans/P1/duplicate").then().statusCode(201).body("vintage", is(2027));

        admin().contentType(ContentType.JSON)
                .body("{\"parameters\":\"{\\\"allowedRates\\\":[{\\\"acquisition\\\":0.9,\\\"servicing\\\":0.1}]}\"}")
                .when().post("/api/ui/v1/rule-versions/plausibility").then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    /**
     * CR-71: the administrator who created a rule version is refused approval of it himself, with a 403
     * problem response carrying the French separation-of-duties message.
     */
    @Test
    void cr71_admin_cannot_approve_own_version() {
        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"code\":\"PSOD\",\"label\":\"Plan SoD\","
                        + "\"direction\":\"OUTBOUND\",\"vintage\":2026}")
                .when().post("/api/ui/v1/plans").then().statusCode(201);
        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"code\":\"RSOD\",\"label\":\"Règle SoD\","
                        + "\"family\":\"LINEAR\"}")
                .when().post("/api/ui/v1/plans/PSOD/rules").then().statusCode(201);
        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\","
                        + "\"versionAnchor\":\"EVENT_DATE\",\"scope\":\"{\\\"product\\\":[\\\"SOD\\\"]}\","
                        + "\"triggerEventTypes\":[\"PREMIUM_COLLECTED\"],\"basis\":\"COLLECTED_PREMIUM\","
                        + "\"outputNature\":\"LINEAR\",\"parameters\":\"{\\\"allowedRates\\\":[0.15]}\","
                        + "\"priority\":10,\"exclusionGroup\":\"SOD\"}")
                .when().post("/api/ui/v1/plans/PSOD/rules/RSOD/versions").then().statusCode(201);

        admin().when().post("/api/ui/v1/plans/PSOD/rules/RSOD/versions/1/approve?entityCode=ALPTIS_ASSURANCES")
                .then().statusCode(403).contentType("application/problem+json")
                .body("detail", containsString("Séparation des tâches"));

        // The version stays a draft after the refusal.
        admin().when().get("/api/ui/v1/plans/PSOD/rules/RSOD/versions?entityCode=ALPTIS_ASSURANCES")
                .then().statusCode(200).body("[0].status", is("DRAFT"));
    }

    /**
     * Records an agreement and a discount convention and enrolls a distributor to a reserved rule.
     */
    @Test
    void records_agreements_and_enrolls() {
        // Seed a distributor through the import engine.
        given().auth().preemptive().basic("operator-alptis", "operator-alptis").contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"flowCode\":\"distributors\",\"sourceSystem\":\"SESAME\","
                        + "\"fileName\":\"d.csv\",\"content\":\"entity_code;source_system;partner_code;legal_name;"
                        + "distributor_type;status;valid_from\\nALPTIS_ASSURANCES;SESAME;P1;Cabinet;INDEPENDENT;"
                        + "ACTIVE;2026-01-01\\nALPTIS_ASSURANCES;SESAME;P2;Cabinet 2;INDEPENDENT;ACTIVE;2026-01-01\\n\"}")
                .when().post("/api/ui/v1/imports").then().statusCode(201);

        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"type\":\"PARTNERSHIP\",\"partnerCode\":\"P1\","
                        + "\"reference\":\"AGR-1\",\"validFrom\":\"2026-01-01\",\"signed\":true}")
                .when().post("/api/ui/v1/agreements").then().statusCode(201);
        admin().when().get("/api/ui/v1/agreements").then().statusCode(200).body("reference", hasItem("AGR-1"));

        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"partnerCode\":\"P1\",\"validFrom\":\"2026-01-01\","
                        + "\"validTo\":\"2026-12-31\",\"anticipationDays\":360,\"maxContracts\":50,"
                        + "\"unpaidRankThreshold\":2}")
                .when().post("/api/ui/v1/advance-agreements").then().statusCode(201).body("status", is("VALID"));
        admin().when().get("/api/ui/v1/advance-agreements").then().statusCode(200).body("partnerCode", hasItem("P1"));
        admin().when().get("/api/ui/v1/advance-agreements/ALPTIS_ASSURANCES/P1")
                .then().statusCode(200).body("convention.partnerCode", is("P1"));
        admin().when().get("/api/ui/v1/discount-summary/ALPTIS_ASSURANCES")
                .then().statusCode(200).body("partnerCode", hasItem("P1"));

        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"code\":\"RES\",\"label\":\"Réservée\",\"family\":\"PORTFOLIO\"}")
                .when().post("/api/ui/v1/plans/" + createPlan() + "/rules").then().statusCode(201);
        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"partnerCode\":\"P1\",\"agreementRef\":\"AGR-1\","
                        + "\"validFrom\":\"2026-01-01\"}")
                .when().post("/api/ui/v1/plans/RESP/rules/RES/enrollments").then().statusCode(201)
                .body("[0].status", is("ACTIVE"));
        admin().when().get("/api/ui/v1/plans/RESP/rules/RES/enrollments?entityCode=ALPTIS_ASSURANCES")
                .then().statusCode(200).body("partnerCode", hasItem("P1"));

        // Mass enrollment of several distributors in one action.
        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"partnerCodes\":[\"P1\",\"P2\"],"
                        + "\"agreementRef\":\"AGR-1\",\"validFrom\":\"2026-01-01\"}")
                .when().post("/api/ui/v1/plans/RESP/rules/RES/enrollments").then().statusCode(201)
                .body("size()", is(2));

        // An empty partnerCodes list falls back to the unit enrollment.
        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"partnerCode\":\"P2\",\"partnerCodes\":[],"
                        + "\"agreementRef\":\"AGR-1\",\"validFrom\":\"2026-01-01\"}")
                .when().post("/api/ui/v1/plans/RESP/rules/RES/enrollments").then().statusCode(201)
                .body("size()", is(1));
    }

    /**
     * Creates the reserved plan used by the enrollment test and returns its code.
     *
     * @return the plan code
     */
    private static String createPlan() {
        admin().contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"code\":\"RESP\",\"label\":\"Plan réservé\","
                        + "\"direction\":\"INBOUND\",\"vintage\":2026}")
                .when().post("/api/ui/v1/plans").then().statusCode(201);
        return "RESP";
    }

    /**
     * Writes are refused to a non-administrator and reads to a sales representative.
     */
    @Test
    void enforces_access() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis").contentType(ContentType.JSON)
                .body("{\"entityCode\":\"ALPTIS_ASSURANCES\",\"code\":\"P1\",\"label\":\"x\",\"direction\":\"INBOUND\","
                        + "\"vintage\":2026}")
                .when().post("/api/ui/v1/plans").then().statusCode(403);
        given().auth().preemptive().basic("rep-alptis", "rep-alptis")
                .when().get("/api/ui/v1/families").then().statusCode(403);
    }
}
