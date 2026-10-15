package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests of the parameter export/import screen endpoints (PAR-15): a plan exports and imports back
 * under a new code, with the entity partition and role gating.
 */
@QuarkusTest
class ParameterTransferResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String ENTITY = "ALPTIS_ASSURANCES";

    /** Resets the database and seeds a plan with a rule and a version. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
            CommissionPlan plan = new CommissionPlan();
            plan.entity = entity;
            plan.code = "PT-SRC";
            plan.label = "Plan transfert";
            plan.direction = Direction.OUTBOUND;
            plan.vintage = (short) 2026;
            plan.persist();
            Rule rule = new Rule();
            rule.plan = plan;
            rule.code = "PT-R1";
            rule.label = "Règle";
            rule.family = RuleFamily.LINEAR;
            rule.persist();
            RuleVersion version = new RuleVersion();
            version.rule = rule;
            version.versionNo = 1;
            version.status = VersionStatus.ACTIVE;
            version.parameters = "{\"allowedRates\":[0.15]}";
            version.persist();
        });
    }

    /**
     * A plan exports and imports back under a new code.
     */
    @Test
    void exports_and_imports_a_plan() {
        String export = given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/parameters/" + ENTITY + "/plans/PT-SRC/export")
                .then().statusCode(200).body("code", is("PT-SRC")).body("rules[0].code", is("PT-R1"))
                .extract().asString();

        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .contentType(ContentType.JSON).body(export)
                .when().post("/api/ui/v1/parameters/" + ENTITY + "/import?newCode=PT-DST")
                .then().statusCode(200).body("planCode", is("PT-DST"));
    }

    /**
     * A UNEP user may not export an Alptis plan, and a viewer may not import.
     */
    @Test
    void out_of_scope_and_role() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/parameters/" + ENTITY + "/plans/PT-SRC/export")
                .then().statusCode(404);
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON)
                .body("{\"code\":\"X\",\"label\":\"X\",\"direction\":\"OUTBOUND\",\"vintage\":2026,\"rules\":[]}")
                .when().post("/api/ui/v1/parameters/" + ENTITY + "/import?newCode=PT-Z")
                .then().statusCode(403);
    }
}
