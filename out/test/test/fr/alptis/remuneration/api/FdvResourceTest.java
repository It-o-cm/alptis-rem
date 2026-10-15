package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.Employee;
import fr.alptis.remuneration.domain.EmployeeMeasure;
import fr.alptis.remuneration.domain.JobCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.MeasureType;
import fr.alptis.remuneration.domain.ObjectiveType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.SalesObjective;
import fr.alptis.remuneration.domain.VariableComponent;
import fr.alptis.remuneration.domain.VariablePlan;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests of the sales-force endpoints (section 8.14): plans, collaborators, objectives, the
 * monthly run and its results, validation, adjustment, pay export and a commercial's own variable,
 * with the entity partition and role gating.
 */
@QuarkusTest
class FdvResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String ENTITY = "ALPTIS_ASSURANCES";
    private static final String MONTH = "2026-09-01";

    /** Resets and seeds a period, a collaborator, a plan, an objective and a production measure. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
            Period period = new Period();
            period.entity = entity;
            period.periodMonth = LocalDate.parse(MONTH);
            period.state = PeriodState.CONTROLLED;
            period.persist();
            Employee employee = new Employee();
            employee.entity = entity;
            employee.externalId = "E1";
            employee.lastName = "Dupont";
            employee.firstName = "Jean";
            employee.jobCode = JobCode.CAC;
            employee.validFrom = LocalDate.of(2026, 1, 1);
            employee.persist();
            VariablePlan plan = new VariablePlan();
            plan.entity = entity;
            plan.jobCode = JobCode.CAC;
            plan.component = VariableComponent.MONTHLY_INDIVIDUAL;
            plan.envelopeAmount = new BigDecimal("2450.00");
            plan.instalmentAmount = new BigDecimal("146.00");
            plan.validFrom = LocalDate.of(2026, 1, 1);
            plan.validTo = LocalDate.of(2026, 12, 31);
            plan.persist();
            SalesObjective objective = new SalesObjective();
            objective.entity = entity;
            objective.employee = employee;
            objective.jobCode = JobCode.CAC;
            objective.objectiveType = ObjectiveType.INDIVIDUAL_QUANTITATIVE;
            objective.periodFrom = LocalDate.of(2026, 9, 1);
            objective.periodTo = LocalDate.of(2026, 9, 30);
            objective.targetValue = new BigDecimal("1000.00");
            objective.versionNo = 1;
            objective.persist();
            EmployeeMeasure measure = new EmployeeMeasure();
            measure.employee = employee;
            measure.measureType = MeasureType.PRODUCTION;
            measure.dimension = "particulier";
            measure.periodFrom = LocalDate.of(2026, 9, 1);
            measure.periodTo = LocalDate.of(2026, 9, 30);
            measure.value = new BigDecimal("2000.00");
            measure.persist();
            VariablePlan quarterly = new VariablePlan();
            quarterly.entity = entity;
            quarterly.jobCode = JobCode.CAC;
            quarterly.component = VariableComponent.QUARTERLY_QUALITATIVE;
            quarterly.envelopeAmount = new BigDecimal("2000.00");
            quarterly.parameters = "{\"criterionAmount\":666.67,\"developmentComplement\":500}";
            quarterly.validFrom = LocalDate.of(2026, 1, 1);
            quarterly.validTo = LocalDate.of(2026, 12, 31);
            quarterly.persist();
            SalesObjective qualitative = new SalesObjective();
            qualitative.entity = entity;
            qualitative.employee = employee;
            qualitative.jobCode = JobCode.CAC;
            qualitative.objectiveType = ObjectiveType.QUALITATIVE;
            qualitative.periodFrom = LocalDate.of(2026, 1, 1);
            qualitative.periodTo = LocalDate.of(2026, 12, 31);
            qualitative.targetValue = new BigDecimal("10.00");
            qualitative.versionNo = 1;
            qualitative.persist();
            EmployeeMeasure activity = new EmployeeMeasure();
            activity.employee = employee;
            activity.measureType = MeasureType.ACTIVITY;
            activity.dimension = "productivite";
            activity.periodFrom = LocalDate.of(2026, 9, 1);
            activity.periodTo = LocalDate.of(2026, 9, 30);
            activity.value = new BigDecimal("12.00");
            activity.persist();
        });
    }

    /**
     * The plans, collaborators and objectives of the entity are listed.
     */
    @Test
    void lists_plans_collaborators_objectives() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/plans")
                .then().statusCode(200).body("jobCode", hasItem("CAC"));
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/collaborators")
                .then().statusCode(200).body("externalId", hasItem("E1"));
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/objectives")
                .then().statusCode(200).body("type", hasItem("INDIVIDUAL_QUANTITATIVE"));
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/measures")
                .then().statusCode(200).body("measureType", hasItem("PRODUCTION"))
                .body("externalId", hasItem("E1"));
    }

    /**
     * The admin runs the month, the manager validates and adjusts, and Finance exports to pay.
     */
    @Test
    void runs_validates_adjusts_and_exports() {
        int id = given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/run")
                .then().statusCode(200).body("find { it.component == 'MONTHLY_INDIVIDUAL' }.amount", is(146.0f))
                .extract().path("find { it.component == 'MONTHLY_INDIVIDUAL' }.id");

        given().auth().preemptive().basic("manager-alptis", "manager-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/results")
                .then().statusCode(200).body("employee", hasItem("E1"));

        given().auth().preemptive().basic("manager-alptis", "manager-alptis")
                .when().post("/api/ui/v1/fdv/results/" + id + "/validate")
                .then().statusCode(200).body("status", is("VALIDATED"));

        given().auth().preemptive().basic("manager-alptis", "manager-alptis")
                .contentType(ContentType.JSON)
                .body("{\"externalId\":\"E1\",\"amount\":50.00,\"reason\":\"Prime exceptionnelle\"}")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/adjust")
                .then().statusCode(200);

        // Before exporting, the per-person pay synthesis shows E1's total (FDV-14).
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/pay-summary")
                .then().statusCode(200).body("externalId", hasItem("E1"));

        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/pay-export")
                .then().statusCode(200).body("fileName", is("ALPTIS_ASSURANCES_VAR_202609.csv"))
                .body("lines", is(2));

        // After exporting, the synthesis still shows the paid variables (validated and exported).
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/pay-summary")
                .then().statusCode(200).body("externalId", hasItem("E1"));
    }

    /**
     * FDV-01: the business administrator creates a collaborator and updates it; the listing reflects
     * the update. A sales manager may not write, and a UNEP administrator is out of the ALPTIS scope.
     */
    @Test
    void creates_and_updates_a_collaborator() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis").contentType(ContentType.JSON)
                .body("{\"externalId\":\"E2\",\"lastName\":\"Martin\",\"firstName\":\"Alice\",\"jobCode\":\"CAC\","
                        + "\"team\":\"Équipe A\",\"region\":\"Sud\",\"managerExternalId\":null,"
                        + "\"validFrom\":\"2026-01-01\",\"validTo\":null}")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/collaborators")
                .then().statusCode(201).body("externalId", is("E2")).body("team", is("Équipe A"));

        given().auth().preemptive().basic("admin-alptis", "admin-alptis").contentType(ContentType.JSON)
                .body("{\"lastName\":\"Martin\",\"firstName\":\"Alice\",\"jobCode\":\"RB\",\"team\":\"Équipe B\","
                        + "\"region\":\"Nord\",\"managerExternalId\":null,\"validFrom\":\"2026-02-01\","
                        + "\"validTo\":\"2026-12-31\"}")
                .when().put("/api/ui/v1/fdv/" + ENTITY + "/collaborators/E2")
                .then().statusCode(200).body("jobCode", is("RB")).body("team", is("Équipe B"));

        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/collaborators")
                .then().statusCode(200).body("findAll { it.externalId == 'E2' }.jobCode", hasItem("RB"));

        // A sales manager may not create a collaborator.
        given().auth().preemptive().basic("manager-alptis", "manager-alptis").contentType(ContentType.JSON)
                .body("{\"externalId\":\"E3\",\"lastName\":\"X\",\"firstName\":\"Y\",\"jobCode\":\"CAC\","
                        + "\"validFrom\":\"2026-01-01\"}")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/collaborators").then().statusCode(403);

        // A UNEP administrator is out of the ALPTIS partition.
        given().auth().preemptive().basic("admin-unep", "admin-unep").contentType(ContentType.JSON)
                .body("{\"externalId\":\"E4\",\"lastName\":\"X\",\"firstName\":\"Y\",\"jobCode\":\"CAC\","
                        + "\"validFrom\":\"2026-01-01\"}")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/collaborators").then().statusCode(404);
    }

    /**
     * FDV-04: the business administrator creates then updates a variable plan (same key, in place); a
     * sales manager may not write.
     */
    @Test
    void creates_and_updates_a_plan() {
        String body = "{\"jobCode\":\"DR\",\"component\":\"MONTHLY_INDIVIDUAL\",\"envelopeAmount\":7700,"
                + "\"instalmentAmount\":458,\"parameters\":\"{\\\"absenceDays\\\":10}\","
                + "\"validFrom\":\"2026-01-01\",\"validTo\":null}";
        given().auth().preemptive().basic("admin-alptis", "admin-alptis").contentType(ContentType.JSON).body(body)
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/plans")
                .then().statusCode(201).body("jobCode", is("DR"))
                .body("parameters", is("{\"absenceDays\":10}"));
        given().auth().preemptive().basic("admin-alptis", "admin-alptis").contentType(ContentType.JSON)
                .body("{\"jobCode\":\"DR\",\"component\":\"MONTHLY_INDIVIDUAL\",\"envelopeAmount\":7800,"
                        + "\"instalmentAmount\":460,\"parameters\":null,\"validFrom\":\"2026-01-01\",\"validTo\":null}")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/plans")
                .then().statusCode(201).body("parameters", org.hamcrest.Matchers.nullValue());
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/plans")
                .then().statusCode(200).body("findAll { it.jobCode == 'DR' }.size()", is(1));

        given().auth().preemptive().basic("manager-alptis", "manager-alptis").contentType(ContentType.JSON).body(body)
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/plans").then().statusCode(403);
    }

    /**
     * FDV-03: the business administrator creates a versioned objective (a second create of the same key
     * yields version 2); a sales manager may not write.
     */
    @Test
    void creates_versioned_objectives() {
        String body = "{\"employeeExternalId\":\"E1\",\"jobCode\":\"CAC\","
                + "\"objectiveType\":\"INDIVIDUAL_QUANTITATIVE\",\"periodFrom\":\"2026-01-01\","
                + "\"periodTo\":\"2026-12-31\",\"targetValue\":1500}";
        given().auth().preemptive().basic("admin-alptis", "admin-alptis").contentType(ContentType.JSON).body(body)
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/objectives")
                .then().statusCode(201).body("versionNo", is(1)).body("employee", is("E1"));
        given().auth().preemptive().basic("admin-alptis", "admin-alptis").contentType(ContentType.JSON).body(body)
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/objectives")
                .then().statusCode(201).body("versionNo", is(2));

        given().auth().preemptive().basic("manager-alptis", "manager-alptis").contentType(ContentType.JSON).body(body)
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/objectives").then().statusCode(403);

        // A collective objective (no employee) comes back with a null employee.
        given().auth().preemptive().basic("admin-alptis", "admin-alptis").contentType(ContentType.JSON)
                .body("{\"employeeExternalId\":null,\"jobCode\":\"RR\",\"objectiveType\":\"COLLECTIVE_QUANTITATIVE\","
                        + "\"periodFrom\":\"2026-01-01\",\"periodTo\":\"2026-12-31\",\"targetValue\":50000}")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/objectives")
                .then().statusCode(201).body("employee", org.hamcrest.Matchers.nullValue()).body("versionNo", is(1));
    }

    /**
     * FDV-06: the business administrator launches the annual regularization of January N+1; a sales rep
     * may not.
     */
    @Test
    void runs_annual_regularization() {
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
            Period january = new Period();
            january.entity = entity;
            january.periodMonth = LocalDate.of(2027, 1, 1);
            january.state = PeriodState.CONTROLLED;
            january.persist();
        });
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/2026/annual-regularization")
                .then().statusCode(200).body("regularized", is(1));
        given().auth().preemptive().basic("rep-alptis", "rep-alptis")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/2026/annual-regularization").then().statusCode(403);
    }

    /**
     * FDV-07: the business administrator launches the quarterly qualitative variable of the quarter
     * ending at the seeded month; a sales rep may not.
     */
    @Test
    void runs_quarterly_qualitative() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/quarterly-qualitative")
                .then().statusCode(200).body("computed", is(1));
        given().auth().preemptive().basic("rep-alptis", "rep-alptis")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/quarterly-qualitative").then().statusCode(403);
    }

    /**
     * FDV-16: a commercial simulates a month with a hypothetical production (objective met) and the
     * measured one; a viewer is refused.
     */
    @Test
    void simulates_the_variable() {
        given().auth().preemptive().basic("rep-alptis", "rep-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/simulate/E1?month=" + MONTH + "&production=2000")
                .then().statusCode(200).body("achieved", is(true)).body("amount", is(146.0f));
        given().auth().preemptive().basic("rep-alptis", "rep-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/simulate/E1?month=" + MONTH)
                .then().statusCode(200).body("weightedProduction", org.hamcrest.Matchers.notNullValue());
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/simulate/E1?month=" + MONTH).then().statusCode(403);
    }

    /**
     * A commercial consults its own variable; a UNEP user is out of scope and a viewer may not run.
     */
    @Test
    void my_variable_scope_and_role() {
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/run").then().statusCode(200);
        given().auth().preemptive().basic("rep-alptis", "rep-alptis")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/my-variable/E1")
                .then().statusCode(200).body("employee", hasItem("E1"));
        given().auth().preemptive().basic("finance-unep", "finance-unep")
                .when().get("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/results").then().statusCode(404);
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().post("/api/ui/v1/fdv/" + ENTITY + "/" + MONTH + "/run").then().statusCode(403);
    }
}
