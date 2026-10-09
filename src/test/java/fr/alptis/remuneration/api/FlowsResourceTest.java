package fr.alptis.remuneration.api;

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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests of the flow screens endpoints: the catalogue (ING-10), the exchange-format reference
 * (section 9.9) and the export journal with the on-demand production and download (REP-04), with the
 * entity partition and role gating.
 */
@QuarkusTest
class FlowsResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String ENTITY = "ALPTIS_ASSURANCES";
    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds a period with a commission line to export. */
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
            CommissionLine line = new CommissionLine();
            line.entity = entity;
            line.period = period;
            line.direction = Direction.OUTBOUND;
            line.nature = LineNature.LINEAR;
            line.status = LineStatus.EARNED;
            line.amount = new BigDecimal("15.00");
            line.persist();
        });
    }

    /**
     * The catalogue lists every flow and filters by direction; the formats reference is served.
     */
    @Test
    void catalogue_and_formats() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/flows/catalog")
                .then().statusCode(200).body("size()", is(16)).body("id", hasItem("OUT-05"));
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/flows/catalog?direction=INBOUND")
                .then().statusCode(200).body("size()", is(9));
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/flows/formats")
                .then().statusCode(200).body("files.flow", hasItem("OUT-02"));
    }

    /**
     * An on-demand export of a period is produced, journalled and downloaded identically.
     */
    @Test
    void produce_journal_and_download() {
        int id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/flows/" + ENTITY + "/exports?object=COMMISSION_LINES&period=" + MONTH)
                .then().statusCode(200).body("flowCode", is("OUT-04:COMMISSION_LINES:" + MONTH))
                .extract().path("id");

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/flows/" + ENTITY + "/exports")
                .then().statusCode(200).body("id", hasItem(id));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/flows/exports/" + id + "/content")
                .then().statusCode(200).body(containsString("line_id;nature;status"));
    }

    /**
     * A Data export without a period is produced for an object.
     */
    @Test
    void data_export_without_period() {
        given().auth().preemptive().basic("finance-alptis", "finance-alptis")
                .when().post("/api/ui/v1/flows/" + ENTITY + "/exports?object=ACCOUNTS")
                .then().statusCode(200).body("flowCode", is("OUT-05:ACCOUNTS"));
    }

    /**
     * A UNEP user may not produce an Alptis export, a viewer may not produce at all, and an unknown
     * export download is not found.
     */
    @Test
    void out_of_scope_role_and_unknown() {
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().post("/api/ui/v1/flows/" + ENTITY + "/exports?object=ACCOUNTS")
                .then().statusCode(404);
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .when().post("/api/ui/v1/flows/" + ENTITY + "/exports?object=ACCOUNTS")
                .then().statusCode(403);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/flows/exports/999999/content")
                .then().statusCode(404);

        // An export of another entity is out of scope for its content download.
        int id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/flows/" + ENTITY + "/exports?object=ACCOUNTS")
                .then().statusCode(200).extract().path("id");
        given().auth().preemptive().basic("operator-unep", "operator-unep")
                .when().get("/api/ui/v1/flows/exports/" + id + "/content")
                .then().statusCode(404);
    }
}
