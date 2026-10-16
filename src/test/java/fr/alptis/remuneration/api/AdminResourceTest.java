package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PersonType;
import fr.alptis.remuneration.domain.Policyholder;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * HTTP tests of the technical administration endpoints (section 3, 8.13): the supervision of
 * treatments and flows (OUT-07, NFR-16) and the purge/anonymisation of personal data (ADM-06),
 * reserved to the technical administrator.
 */
@QuarkusTest
class AdminResourceTest {

    @Inject
    TestDatabaseReset reset;

    private static final String ENTITY = "ALPTIS_ASSURANCES";

    /** Resets and seeds an eligible (long-ended) policyholder and an active one. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", ENTITY).firstResult();
            policyholder(entity, "OLD", LocalDate.of(2000, 1, 1));
            policyholder(entity, "ACTIVE", null);
        });
    }

    /** Creates a policyholder with a single contract ended at a date (null = still active). */
    private static void policyholder(LegalEntity entity, String name, LocalDate endDate) {
        Policyholder holder = new Policyholder();
        holder.entity = entity;
        holder.sourceSystem = "SYS";
        holder.externalId = name;
        holder.lastName = name;
        holder.firstName = "Jean";
        holder.birthDate = LocalDate.of(1970, 1, 1);
        holder.personType = PersonType.NATURAL;
        holder.persist();
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "SYS";
        contract.contractNumber = "C-" + name;
        contract.policyholder = holder;
        contract.endDate = endDate;
        contract.persist();
    }

    /**
     * The supervision reports a healthy status with its metrics for the technical administrator; an
     * operator is refused.
     */
    @Test
    void supervision_reports_health() {
        given().auth().preemptive().basic("it-admin", "it-admin")
                .when().get("/api/ui/v1/admin/supervision")
                .then().statusCode(200).body("status", is("UP"));
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/admin/supervision")
                .then().statusCode(403);
    }

    /**
     * The purge anonymises the policyholder whose contracts all ended beyond the retention period, and
     * only that one; it is idempotent and refused to a non-administrator.
     */
    @Test
    void purge_anonymises_the_eligible_records() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/admin/purge/eligible")
                .then().statusCode(403);

        // A preview reports no preserved amount.
        given().auth().preemptive().basic("it-admin", "it-admin")
                .when().get("/api/ui/v1/admin/purge/eligible")
                .then().statusCode(200).body("count", is(1)).body("preservedAmount", nullValue());

        // The purge anonymises the one eligible record and reports the preserved amount (no commission
        // line was seeded, so it is zero).
        given().auth().preemptive().basic("it-admin", "it-admin")
                .when().post("/api/ui/v1/admin/purge")
                .then().statusCode(200).body("count", is(1)).body("preservedAmount", is(0));

        // Idempotent: nothing left to anonymise.
        given().auth().preemptive().basic("it-admin", "it-admin")
                .when().get("/api/ui/v1/admin/purge/eligible")
                .then().statusCode(200).body("count", is(0));
    }
}
