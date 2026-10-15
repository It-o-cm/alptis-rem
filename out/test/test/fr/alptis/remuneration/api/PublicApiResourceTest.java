package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementStatus;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.domain.StatementStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * HTTP tests of the public read API (section 9.8): a distributor's accounts, commission lines,
 * statements and escompte agreements, the periods and the import deposit and result, with the entity
 * isolation of CR-70.
 */
@QuarkusTest
class PublicApiResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String ENTITY = "ALPTIS_ASSURANCES";
    private static final String MONTH = "2026-09-01";
    private static final String CODE = "P-API";

    /** Resets the database and seeds a distributor with an account, a line, a statement and an agreement. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
            Distributor distributor = new Distributor();
            distributor.partnerCode = CODE;
            distributor.legalName = "Cabinet API";
            distributor.oriasStatus = OriasStatus.REGISTERED;
            distributor.persist();
            Period period = new Period();
            period.entity = entity;
            period.periodMonth = LocalDate.parse(MONTH);
            period.state = PeriodState.CONTROLLED;
            period.persist();
            DistributorAccount account = new DistributorAccount();
            account.entity = entity;
            account.payeeType = PayeeType.DISTRIBUTOR;
            account.payeeId = distributor.getId();
            account.persist();
            CommissionLine line = new CommissionLine();
            line.entity = entity;
            line.period = period;
            line.account = account;
            line.originDistributor = distributor;
            line.direction = Direction.OUTBOUND;
            line.nature = LineNature.LINEAR;
            line.status = LineStatus.EARNED;
            line.amount = new BigDecimal("30.00");
            line.persist();
            // A period-less line (e.g. an opening adjustment) for the same distributor.
            CommissionLine orphan = new CommissionLine();
            orphan.entity = entity;
            orphan.originDistributor = distributor;
            orphan.direction = Direction.OUTBOUND;
            orphan.nature = LineNature.MANUAL;
            orphan.status = LineStatus.EARNED;
            orphan.amount = new BigDecimal("5.00");
            orphan.persist();
            Statement statement = new Statement();
            statement.entity = entity;
            statement.period = period;
            statement.account = account;
            statement.statementNumber = "ST-API";
            statement.versionNo = 1;
            statement.netAmount = new BigDecimal("30.00");
            statement.status = StatementStatus.DRAFT;
            statement.persist();
            AdvanceAgreement agreement = new AdvanceAgreement();
            agreement.entity = entity;
            agreement.distributor = distributor;
            agreement.validFrom = LocalDate.of(2026, 1, 1);
            agreement.maxContracts = 10;
            agreement.status = AdvanceAgreementStatus.VALID;
            agreement.persist();
        });
    }

    /**
     * The distributor resources return the account balance, the commission lines (with their filters),
     * the statements and the escompte agreements of the distributor.
     */
    @Test
    void distributor_resources() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/distributors/" + CODE + "/accounts")
                .then().statusCode(200).body("[0].entity", is(ENTITY)).body("[0].blocked", is(false));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/distributors/" + CODE + "/commission-lines?period=" + MONTH
                        + "&nature=LINEAR&status=EARNED")
                .then().statusCode(200).body("nature", hasItem("LINEAR")).body("[0].amount", is(30.0f));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/distributors/" + CODE + "/statements")
                .then().statusCode(200).body("statementNumber", hasItem("ST-API"));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/distributors/" + CODE + "/advance-agreements")
                .then().statusCode(200).body("[0].status", is("VALID")).body("[0].maxContracts", is(10));
    }

    /**
     * BRD-10: the distributor's annual tax certificates (attestations) are published through the API —
     * the current year by default and for an explicit year — totalling its own paid commissions only.
     */
    @Test
    void tax_certificates_published() {
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
            Distributor mine = Distributor.<Distributor>find("partnerCode", CODE).firstResult();
            paidLine(entity, mine, "42.00");
            // Another distributor's paid line of the same year is excluded from this distributor's certificate.
            Distributor other = new Distributor();
            other.partnerCode = "OTHER-API";
            other.legalName = "Autre";
            other.oriasStatus = OriasStatus.REGISTERED;
            other.persist();
            paidLine(entity, other, "999.00");
        });

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/distributors/" + CODE + "/tax-certificates?year=2026")
                .then().statusCode(200).body("[0].total", is(42.0f)).body("[0].year", is(2026));
        // Without a year, the current fiscal year is used (the clock is in 2026).
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/distributors/" + CODE + "/tax-certificates")
                .then().statusCode(200).body("total", hasItem(42.0f));
    }

    /** Persists a paid commission line attached to September 2026 for a distributor. */
    private static void paidLine(LegalEntity entity, Distributor distributor, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.originDistributor = distributor;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = LineStatus.PAID;
        line.amount = new BigDecimal(amount);
        line.attachmentMonth = LocalDate.parse(MONTH);
        line.persist();
    }

    /**
     * The periods resource returns the state of the entity's periods, and the import deposit returns a
     * result readable by its id.
     */
    @Test
    void periods_and_imports() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/periods")
                .then().statusCode(200).body("find { it.month == '" + MONTH + "' }.state", is("CONTROLLED"));

        int id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType("text/plain")
                .body("source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n")
                .when().post("/api/v1/imports/events?entity=" + ENTITY + "&sourceSystem=SYS&fileName=f.csv")
                .then().statusCode(200).body("flowCode", is("events")).body("read", notNullValue())
                .extract().path("id");

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/imports/" + id)
                .then().statusCode(200).body("id", is(id));
    }

    /**
     * Cross-entity isolation (CR-70): a UNEP user sees no Alptis data and an explicit Alptis entity is
     * out of scope; an unknown distributor and an unknown import are not found.
     */
    @Test
    void isolation_and_unknown() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/v1/distributors/" + CODE + "/accounts")
                .then().statusCode(200).body("size()", is(0));

        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/v1/distributors/" + CODE + "/accounts?entity=" + ENTITY)
                .then().statusCode(404);

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/distributors/GHOST/accounts")
                .then().statusCode(404);

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/imports/999999")
                .then().statusCode(404);

        given().when().get("/api/v1/periods").then().statusCode(401);
    }

    /**
     * An explicit visible entity narrows the read; the commission lines read without any filter; a
     * deposit and an import result of another entity are out of scope.
     */
    @Test
    void entity_filter_and_import_scope() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/distributors/" + CODE + "/accounts?entity=" + ENTITY)
                .then().statusCode(200).body("[0].entity", is(ENTITY));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/v1/distributors/" + CODE + "/commission-lines")
                .then().statusCode(200).body("nature", hasItem("LINEAR"));

        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .contentType("text/plain")
                .body("source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n")
                .when().post("/api/v1/imports/events?entity=" + ENTITY + "&sourceSystem=SYS&fileName=f.csv")
                .then().statusCode(404);

        int id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType("text/plain")
                .body("source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount\n")
                .when().post("/api/v1/imports/events?entity=" + ENTITY + "&sourceSystem=SYS&fileName=f.csv")
                .then().statusCode(200).extract().path("id");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/v1/imports/" + id).then().statusCode(404);
    }

    /**
     * An auditor, unrestricted across entities, reads the distributor's accounts without an entity
     * filter.
     */
    @Test
    void unrestricted_auditor_reads_across_entities() {
        given().auth().preemptive().basic("auditor", "auditor")
                .when().get("/api/v1/distributors/" + CODE + "/accounts")
                .then().statusCode(200).body("entity", hasItem(ENTITY));
    }
}
