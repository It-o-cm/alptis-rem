package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.RemunerationChoice;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link CoverageImportService}: a valid line creates the contract and coverage graph, an
 * unknown product is put on hold, and malformed lines are rejected with their codes.
 */
@QuarkusTest
class CoverageImportServiceTest {

    private static final String HEADER =
            "entity_code;source_system;contract_number;coverage_code;product_code;policyholder_id;"
                    + "registration_date;effect_date;status;remuneration_mode;linear_rate;production_code;annual_premium";

    @Inject
    CoverageImportService service;

    private void seedReferential() {
        LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
        Product product = new Product();
        product.entity = alptis;
        product.code = "SANTE_SELECT";
        product.label = "Santé Select";
        product.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = alptis;
        pc.code = "61723";
        pc.label = "Agence";
        pc.persist();
    }

    private static String row(String contract, String coverage, String product, String status, String prodCode) {
        return "ALPTIS_ASSURANCES;CLEVA;" + contract + ";" + coverage + ";" + product + ";SUB-1;"
                + "2026-01-01;2026-01-01;" + status + ";LINEAR;0.15;" + prodCode + ";1000.00";
    }

    /**
     * A valid line creates the contract, coverage, remuneration choice and main assignment.
     */
    @Test
    @TestTransaction
    void loads_a_coverage_graph() {
        seedReferential();
        String content = HEADER + "\n" + row("AD-1", "C1", "SANTE_SELECT", "IN_FORCE", "61723") + "\n";

        ImportBatch batch = service.importCoverages("ALPTIS_ASSURANCES", "CLEVA", "cov.csv", content);

        assertEquals(1, batch.linesLoaded);
        Coverage coverage = Coverage.find("coverageCode", "C1").firstResult();
        assertNotNull(coverage);
        assertEquals(0, coverage.annualPremium.compareTo(new java.math.BigDecimal("1000.00")));
        RemunerationChoice choice = RemunerationChoice.find("coverage", coverage).firstResult();
        assertEquals(0, choice.linearRate.compareTo(new java.math.BigDecimal("0.15")));
        CoverageAssignment assignment = CoverageAssignment.find("coverage", coverage).firstResult();
        assertEquals("61723", assignment.productionCode.code);
    }

    /**
     * An unknown product puts the line on hold; a missing coverage code and a bad status are rejected.
     */
    @Test
    @TestTransaction
    void holds_unknown_product_and_rejects_malformed_lines() {
        seedReferential();
        String content = HEADER + "\n"
                + row("AD-2", "C2", "UNKNOWN_PRODUCT", "IN_FORCE", "61723") + "\n"
                + row("AD-3", "", "SANTE_SELECT", "IN_FORCE", "61723") + "\n"
                + row("AD-4", "C4", "SANTE_SELECT", "BOGUS_STATUS", "61723") + "\n";

        ImportBatch batch = service.importCoverages("ALPTIS_ASSURANCES", "CLEVA", "cov2.csv", content);

        assertEquals(0, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(2, batch.linesRejected);
    }

    /**
     * Re-importing updates an existing coverage rather than creating a duplicate.
     */
    @Test
    @TestTransaction
    void updates_an_existing_coverage() {
        seedReferential();
        service.importCoverages("ALPTIS_ASSURANCES", "CLEVA", "cov.csv",
                HEADER + "\n" + row("AD-1", "C1", "SANTE_SELECT", "IN_FORCE", "61723") + "\n");
        service.importCoverages("ALPTIS_ASSURANCES", "CLEVA", "cov-v2.csv",
                HEADER + "\n" + row("AD-1", "C1", "SANTE_SELECT", "TERMINATED", "61723") + "\n");

        assertEquals(1, Coverage.count("coverageCode", "C1"));
        Coverage coverage = Coverage.find("coverageCode", "C1").firstResult();
        assertEquals("TERMINATED", coverage.status.name());
    }

    /**
     * Exercises the remaining branches: held on an unknown production code, the type errors for mode
     * and both dates, and the optional-field branches (holder name, advance flag, empty and
     * malformed rates).
     */
    @Test
    @TestTransaction
    void covers_remaining_branches() {
        seedReferential();
        String h = HEADER + ";policyholder_name;advance_requested\n";
        String content = h
                + "ALPTIS_ASSURANCES;CLEVA;AD-1;C1;SANTE_SELECT;SUB-1;2026-01-01;2026-01-01;IN_FORCE;LINEAR;;61723;xx;Nom;true\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-2;C2;SANTE_SELECT;SUB-1;2026-01-01;2026-01-01;IN_FORCE;LINEAR;0.1;NOPE;100;;\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-3;C3;SANTE_SELECT;SUB-1;2026-01-01;2026-01-01;IN_FORCE;BOGUS;0.1;61723;100;;\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-4;C4;SANTE_SELECT;SUB-1;2026-01-01;notadate;IN_FORCE;LINEAR;0.1;61723;100;;\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-5;C5;SANTE_SELECT;SUB-1;notadate;2026-01-01;IN_FORCE;LINEAR;0.1;61723;100;;\n";

        ImportBatch batch = service.importCoverages("ALPTIS_ASSURANCES", "CLEVA", "cov-all.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(3, batch.linesRejected);
        Coverage c1 = Coverage.find("coverageCode", "C1").firstResult();
        assertNull(c1.annualPremium);
        RemunerationChoice choice = RemunerationChoice.find("coverage", c1).firstResult();
        assertNull(choice.linearRate);
        assertEquals(Boolean.TRUE, choice.advanceRequested);
    }

    /**
     * Re-importing the very same file is refused.
     */
    @Test
    @TestTransaction
    void refuses_reimport_of_the_same_file() {
        seedReferential();
        String content = HEADER + "\n" + row("AD-1", "C1", "SANTE_SELECT", "IN_FORCE", "61723") + "\n";
        service.importCoverages("ALPTIS_ASSURANCES", "CLEVA", "same.csv", content);
        assertThrows(ConflictException.class,
                () -> service.importCoverages("ALPTIS_ASSURANCES", "CLEVA", "same.csv", content));
    }

    /**
     * An unknown entity is refused.
     */
    @Test
    @TestTransaction
    void refuses_an_unknown_entity() {
        assertThrows(ConflictException.class,
                () -> service.importCoverages("NOPE", "CLEVA", "x.csv", HEADER + "\n"));
    }
}
