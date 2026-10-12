package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CalculationRun;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.RunStatus;
import fr.alptis.remuneration.domain.RunType;
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests of the simulation screen endpoint (PAR-13): the substitution of a grid rate and the
 * comparison, with the entity partition and role gating.
 */
@QuarkusTest
class SimulationResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String ENTITY = "ALPTIS_ASSURANCES";
    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds a period, a real run and a line produced by rule SIM-APP v2. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
            CommissionPlan plan = new CommissionPlan();
            plan.entity = entity;
            plan.code = "SIM-PLAN";
            plan.label = "Plan simulation";
            plan.direction = Direction.OUTBOUND;
            plan.persist();
            Rule rule = new Rule();
            rule.plan = plan;
            rule.code = "SIM-APP";
            rule.label = "Apport";
            rule.family = RuleFamily.LINEAR;
            rule.persist();
            RuleVersion version = new RuleVersion();
            version.rule = rule;
            version.versionNo = 2;
            version.status = VersionStatus.DRAFT;
            version.persist();
            Period period = new Period();
            period.entity = entity;
            period.periodMonth = LocalDate.parse(MONTH);
            period.state = PeriodState.CONTROLLED;
            period.persist();
            CalculationRun run = new CalculationRun();
            run.period = period;
            run.runType = RunType.ACTUAL;
            run.status = RunStatus.COMPLETED;
            run.persist();
            Distributor distributor = new Distributor();
            distributor.partnerCode = "D-SIM";
            distributor.legalName = "Cabinet Sim";
            distributor.oriasStatus = OriasStatus.REGISTERED;
            distributor.persist();
            CommissionLine line = new CommissionLine();
            line.entity = entity;
            line.period = period;
            line.run = run;
            line.direction = Direction.OUTBOUND;
            line.nature = LineNature.ACQUISITION;
            line.status = LineStatus.EARNED;
            line.ruleVersion = version;
            line.basisAmount = new BigDecimal("1000.00");
            line.rate = new BigDecimal("0.30");
            line.amount = new BigDecimal("300.00");
            line.originDistributor = distributor;
            line.persist();
        });
    }

    /**
     * Simulating the apport rate from 30 % to 25 % recomputes the line and returns the gap.
     */
    @Test
    void simulates_a_rate_substitution() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .contentType(ContentType.JSON)
                .body("{\"ruleCode\":\"SIM-APP\",\"versionNo\":2,\"substitutions\":[{\"oldRate\":0.30,"
                        + "\"newRate\":0.25}]}")
                .when().post("/api/ui/v1/simulation/" + ENTITY + "/" + MONTH)
                .then().statusCode(200)
                .body("simulatedTotal", is(250.0f))
                .body("ecart", is(-50.0f))
                .body("linesModified", is(1))
                .body("held", is(0));

        // Without a substitution table the lines are replayed unchanged.
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .contentType(ContentType.JSON)
                .body("{\"ruleCode\":\"SIM-APP\",\"versionNo\":2}")
                .when().post("/api/ui/v1/simulation/" + ENTITY + "/" + MONTH)
                .then().statusCode(200).body("linesModified", is(0));

        // The history lists the simulation just run (PAR-13).
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/simulation/" + ENTITY + "/history")
                .then().statusCode(200).body("ruleCode", org.hamcrest.Matchers.hasItem("SIM-APP"));
    }

    /**
     * A UNEP user may not simulate on Alptis, and a viewer may not simulate at all.
     */
    @Test
    void out_of_scope_and_role() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .contentType(ContentType.JSON)
                .body("{\"ruleCode\":\"SIM-APP\",\"versionNo\":2,\"substitutions\":[]}")
                .when().post("/api/ui/v1/simulation/" + ENTITY + "/" + MONTH)
                .then().statusCode(404);
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON)
                .body("{\"ruleCode\":\"SIM-APP\",\"versionNo\":2,\"substitutions\":[]}")
                .when().post("/api/ui/v1/simulation/" + ENTITY + "/" + MONTH)
                .then().statusCode(403);
        // The history is partitioned too: a UNEP user cannot read the Alptis history.
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/simulation/" + ENTITY + "/history")
                .then().statusCode(404);
    }
}
