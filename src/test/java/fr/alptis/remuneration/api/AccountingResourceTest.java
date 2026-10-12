package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AccountingEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests of the accounting screens endpoints: the schema and mapping (FIN-02), the period entries
 * (FIN-01/05/07), the cut-off pilotage (FIN-04/09) and the extraction at closing (FIN-03), with the
 * entity partition.
 */
@QuarkusTest
class AccountingResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String ENTITY = "ALPTIS_ASSURANCES";
    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds a period with a forecast line, an earned line and two entries. */
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
            line(entity, period, LineStatus.FORECAST, "180.00");
            line(entity, period, LineStatus.EARNED, "15.00");
            entry(entity, period, AccountingEvent.FORECAST_RECOGNITION, "180.00");
            entry(entity, period, AccountingEvent.EARNING, "15.00");
        });
    }

    /** Creates a commission line. */
    private static void line(LegalEntity entity, Period period, LineStatus status, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = status;
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /** Creates an accounting entry. */
    private static void entry(LegalEntity entity, Period period, AccountingEvent event, String amount) {
        AccountingEntry e = new AccountingEntry();
        e.entity = entity;
        e.period = period;
        e.accountingEvent = event;
        e.journal = "COM";
        e.entryDate = LocalDate.of(2026, 9, 30);
        e.debitAccount = "428600";
        e.creditAccount = "467200";
        e.amount = new BigDecimal(amount);
        e.fiscalYear = (short) 2026;
        e.accountingMonth = LocalDate.parse(MONTH);
        e.persist();
    }

    /**
     * The schema lists the events and the mapping the ten account roles.
     */
    @Test
    void scheme_lists_events_and_mapping() {
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/accounting/" + ENTITY + "/scheme")
                .then().statusCode(200)
                .body("schema.event", hasItem("FORECAST_RECOGNITION"))
                .body("mapping.size()", is(10));
    }

    /**
     * The entries search returns the period's entries; the cut-off pilotage returns the figures.
     */
    @Test
    void entries_and_cutoff() {
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/accounting/" + ENTITY + "/" + MONTH + "/entries")
                .then().statusCode(200).body("accountingEvent", hasItem("EARNING"));

        // Cross-month search by account, then the audit chain of one entry (FIN-05, FIN-07).
        Integer entryId = given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/accounting/" + ENTITY + "/entries?account=428600")
                .then().statusCode(200).body("accountingEvent", hasItem("EARNING"))
                .extract().path("[0].id");
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/accounting/" + ENTITY + "/entries/" + entryId + "/chain")
                .then().statusCode(200).body("debitAccount", is("428600"));

        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/accounting/" + ENTITY + "/cutoff?date=2026-09-30&view=ENTITY")
                .then().statusCode(200).body("total.forecast", is(180.0f)).body("total.earned", is(15.0f));

        // Without a view, the entity maille is the default.
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/accounting/" + ENTITY + "/cutoff?date=2026-09-30")
                .then().statusCode(200).body("view", is("ENTITY"));
    }

    /**
     * The extraction is produced at closing, named and fingerprinted, and its CSV is downloadable.
     */
    @Test
    void extraction_produces_and_downloads() {
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().post("/api/ui/v1/accounting/" + ENTITY + "/" + MONTH + "/extract")
                .then().statusCode(200).body("fileRef", is("ALPTIS_ASSURANCES_ECR_202609.csv"))
                .body("status", is("GENERATED"));

        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/accounting/" + ENTITY + "/" + MONTH + "/extract.csv")
                .then().statusCode(200).body(containsString("accounting_event"))
                .body(containsString("EARNING;"));
    }

    /**
     * A UNEP user may not see the Alptis accounting schema, and a viewer may not trigger the
     * extraction.
     */
    @Test
    void out_of_scope_and_role() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/accounting/" + ENTITY + "/scheme")
                .then().statusCode(404);
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().post("/api/ui/v1/accounting/" + ENTITY + "/" + MONTH + "/extract")
                .then().statusCode(403);
    }

    /**
     * The schema of an entity carries at least the fourteen accounting events of the specification.
     */
    @Test
    void schema_covers_the_events() {
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().get("/api/ui/v1/accounting/" + ENTITY + "/scheme")
                .then().statusCode(200).body("schema.size()", greaterThanOrEqualTo(14));
    }
}
