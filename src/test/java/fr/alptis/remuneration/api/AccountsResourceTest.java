package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProductionCode;
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
 * HTTP tests for {@link AccountsResource}: a distributor's accounts and balances (CPT-06), the
 * compensation (CPT-04), the debtor list (CPT-07) and the manual block (CPT-05), with the entity
 * partition and access.
 */
@QuarkusTest
class AccountsResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String MONTH = "2026-09-01";

    /** Resets the database and seeds a distributor with a debtor and a creditor account. */
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
            Distributor dx = new Distributor();
            dx.partnerCode = "DX";
            dx.legalName = "DX";
            dx.persist();
            line(alptis, period, pcAccount(alptis, dx, "DX-1"), "-200");
            line(alptis, period, pcAccount(alptis, dx, "DX-2"), "500");
        });
    }

    /** Creates a production-code account for a distributor. */
    private static DistributorAccount pcAccount(LegalEntity entity, Distributor distributor, String code) {
        ProductionCode pc = new ProductionCode();
        pc.entity = entity;
        pc.code = code;
        pc.distributor = distributor;
        pc.persist();
        DistributorAccount account = new DistributorAccount();
        account.entity = entity;
        account.payeeType = PayeeType.PRODUCTION_CODE;
        account.payeeId = pc.getId();
        account.persist();
        return account;
    }

    /** Posts a line on an account. */
    private static void line(LegalEntity entity, Period period, DistributorAccount account, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.persist();
    }

    /**
     * The distributor view totals its accounts; compensation nets them without changing the total.
     */
    @Test
    void view_and_compensate() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/accounts/ALPTIS_ASSURANCES/distributor/DX")
                .then().statusCode(200).body("total", is(300.0f));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/accounts/ALPTIS_ASSURANCES/" + MONTH + "/distributor/DX/compensate")
                .then().statusCode(200).body("transfers", is(2));
    }

    /**
     * The debtor list includes the account with a negative balance.
     */
    @Test
    void lists_debtors() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/accounts/ALPTIS_ASSURANCES/debtors")
                .then().statusCode(200).body("balance", hasItem(-200.0f));
    }

    /**
     * An operator blocks then unblocks an account.
     */
    @Test
    void block_and_unblock() {
        Integer id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/accounts/ALPTIS_ASSURANCES/distributor/DX")
                .then().statusCode(200).extract().path("accounts[0].id");

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body("{\"reason\":\"litige\",\"until\":\"2026-12-31\"}")
                .when().post("/api/ui/v1/accounts/" + id + "/block")
                .then().statusCode(200).body("blocked", is(true));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().post("/api/ui/v1/accounts/" + id + "/unblock")
                .then().statusCode(200).body("blocked", is(false));
    }

    /**
     * A distributor view of another entity is out of scope, and an unknown account cannot be blocked.
     */
    @Test
    void scope_and_unknown() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/accounts/UNEP/distributor/DX")
                .then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .contentType(ContentType.JSON).body("{\"reason\":\"x\"}")
                .when().post("/api/ui/v1/accounts/999999/block")
                .then().statusCode(404);
    }

    /**
     * An unknown distributor, an unknown partner or month to compensate, and an unknown entity (seen
     * by an unrestricted auditor) are all reported as not found.
     */
    @Test
    void not_found_paths() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
            .when().get("/api/ui/v1/accounts/ALPTIS_ASSURANCES/distributor/NOPE")
            .then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
            .when().post("/api/ui/v1/accounts/ALPTIS_ASSURANCES/" + MONTH + "/distributor/NOPE/compensate")
            .then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
            .when().post("/api/ui/v1/accounts/ALPTIS_ASSURANCES/2020-01-01/distributor/DX/compensate")
            .then().statusCode(404);
        given().auth().preemptive().basic("auditor", "auditor")
            .when().get("/api/ui/v1/accounts/GHOST/debtors")
            .then().statusCode(404);
    }

    /**
     * An account of another entity cannot be blocked by a restricted operator (out of scope).
     */
    @Test
    void block_account_out_of_scope() {
        long[] id = new long[1];
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity unep = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
            DistributorAccount account = new DistributorAccount();
            account.entity = unep;
            account.payeeType = PayeeType.DISTRIBUTOR;
            account.payeeId = 99L;
            account.persist();
            id[0] = account.getId();
        });
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
            .contentType(ContentType.JSON).body("{\"reason\":\"x\"}")
            .when().post("/api/ui/v1/accounts/" + id[0] + "/block")
            .then().statusCode(404);
    }

    /**
     * An account may be blocked without a lifting date.
     */
    @Test
    void block_without_until() {
        Integer id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
            .when().get("/api/ui/v1/accounts/ALPTIS_ASSURANCES/distributor/DX")
            .then().extract().path("accounts[0].id");
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
            .contentType(ContentType.JSON).body("{\"reason\":\"litige\"}")
            .when().post("/api/ui/v1/accounts/" + id + "/block")
            .then().statusCode(200).body("blocked", is(true));
    }

    /**
     * A viewer may not block an account.
     */
    @Test
    void viewer_cannot_block() {
        Integer id = given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/accounts/ALPTIS_ASSURANCES/distributor/DX")
                .then().extract().path("accounts[0].id");
        given().auth().preemptive().basic("viewer-alptis", "viewer-alptis")
                .contentType(ContentType.JSON).body("{\"reason\":\"x\"}")
                .when().post("/api/ui/v1/accounts/" + id + "/block")
                .then().statusCode(403);
    }
}
