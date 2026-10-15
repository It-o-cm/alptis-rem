package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ContractStatus;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.LegalEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for {@link ContractsResource}: the contract search and the guarantee (adhésion) fiche
 * (sections 5.2, 5.5), partitioned by entity.
 */
@QuarkusTest
class ContractsResourceTest {

    @Inject
    TestDatabaseReset reset;

    /** Resets the database and seeds a contract with one guarantee. */
    @BeforeEach
    void seed() {
        reset.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
            Contract contract = new Contract();
            contract.entity = alptis;
            contract.sourceSystem = "CLEVA";
            contract.contractNumber = "CT-FICHE";
            contract.status = ContractStatus.IN_FORCE;
            contract.persist();
            Coverage coverage = new Coverage();
            coverage.contract = contract;
            coverage.coverageCode = "CV-1";
            coverage.status = ContractStatus.IN_FORCE;
            coverage.persist();
        });
    }

    /**
     * The guarantee fiche is returned for an authorised operator; an unknown guarantee is not found.
     */
    @Test
    void returns_the_coverage_fiche() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/contracts/ALPTIS_ASSURANCES/CT-FICHE/coverages/CV-1")
                .then().statusCode(200).body("coverageCode", is("CV-1")).body("contractNumber", is("CT-FICHE"));

        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/contracts/ALPTIS_ASSURANCES/CT-FICHE/coverages/NOPE")
                .then().statusCode(404);
    }
}
