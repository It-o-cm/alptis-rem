package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for {@link AdjustmentsResource}: submitting an adjustment (REG-01), listing a period's
 * adjustments and the second-user approval (REG-03), with the entity partition and access.
 */
@QuarkusTest
class AdjustmentsResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds a controlled Alptis period. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
            Period period = new Period();
            period.entity = alptis;
            period.periodMonth = LocalDate.parse(MONTH);
            period.state = PeriodState.CONTROLLED;
            period.persist();
        });
    }

    /** Submits a correction adjustment and returns its id. */
    private int submit(String user) {
        return given().auth().preemptive().basic(user, user)
                .contentType(ContentType.JSON)
                .body("{\"type\":\"CORRECTION\",\"amount\":9,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":42,"
                        + "\"reason\":\"taux corrigé\"}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(201).extract().path("id");
    }

    /**
     * An operator submits an adjustment, it appears in the list, and a second user approves it.
     */
    @Test
    void submit_list_and_approve() {
        int id = submit("operator-alptis");
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(200).body("status", hasItem("SUBMITTED"));
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
                .when().post("/api/ui/v1/adjustments/" + id + "/approve")
                .then().statusCode(200).body("status", is("APPLIED"));
    }

    /**
     * A transfer adjustment carries a counterpart account and applies two transfer lines.
     */
    @Test
    void submit_account_transfer_with_counterpart() {
        int id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
            .contentType(ContentType.JSON)
            .body("{\"type\":\"ACCOUNT_TRANSFER\",\"amount\":50,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":11,"
                + "\"reason\":\"transfert\",\"counterpartPayeeType\":\"DISTRIBUTOR\",\"counterpartPayeeId\":22}")
            .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH)
            .then().statusCode(201).extract().path("id");
        given().auth().preemptive().basic("admin-alptis", "admin-alptis")
            .when().post("/api/ui/v1/adjustments/" + id + "/approve")
            .then().statusCode(200).body("status", is("APPLIED"));
    }

    /**
     * Submitting into another entity's period is out of scope.
     */
    @Test
    void submit_out_of_scope_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"type\":\"CORRECTION\",\"amount\":9,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":42,"
                        + "\"reason\":\"x\"}")
                .when().post("/api/ui/v1/adjustments/UNEP/" + MONTH)
                .then().statusCode(404);
    }

    /**
     * Submitting into an unknown period is not found.
     */
    @Test
    void submit_unknown_period_is_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON)
                .body("{\"type\":\"CORRECTION\",\"amount\":9,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":42,"
                        + "\"reason\":\"x\"}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/2020-01-01")
                .then().statusCode(404);
    }

    /**
     * A viewer may not submit an adjustment.
     */
    @Test
    void viewer_cannot_submit() {
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON)
                .body("{\"type\":\"CORRECTION\",\"amount\":9,\"payeeType\":\"DISTRIBUTOR\",\"payeeId\":42,"
                        + "\"reason\":\"x\"}")
                .when().post("/api/ui/v1/adjustments/ALPTIS_ASSURANCES/" + MONTH)
                .then().statusCode(403);
    }
}
