package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.Adjustment;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link AdjustmentImportService}: a pivot file whose well-formed lines become submitted
 * adjustments, skipping lines with a missing field, an unknown type, a non-numeric amount, an unknown
 * payee or an unknown counterpart, with the unknown-entity and unknown-period guards (REG-04).
 */
@QuarkusTest
class AdjustmentImportServiceTest {

    @Inject
    AdjustmentImportService service;

    private static final LocalDate M = LocalDate.of(2026, 9, 1);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates the period and two production codes. */
    private static void scene() {
        Period p = new Period();
        p.entity = unep();
        p.periodMonth = M;
        p.state = PeriodState.CALCULATED;
        p.persist();
        productionCode("PC-X");
        productionCode("PC-Y");
    }

    /** Creates a production code of the entity. */
    private static void productionCode(String code) {
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = code;
        pc.persist();
    }

    private static final String HEADER = "adjustment_type;payee_code;amount;reason;auto_reverse;counterpart_payee_code\n";

    /**
     * A seven-line file creates the two well-formed adjustments (one with a counterpart and auto
     * reversal) and skips the blank, unknown-type, non-numeric, unknown-payee and unknown-counterpart
     * lines.
     */
    @Test
    @TestTransaction
    void imports_the_well_formed_lines_and_skips_the_rest() {
        scene();
        String content = HEADER
                + "CORRECTION;PC-X;100.00;ajustement;;\n"
                + "CORRECTION;PC-X;50.00;;false;\n"
                + "BOGUS;PC-X;50.00;motif;;\n"
                + "CORRECTION;PC-X;abc;motif;;\n"
                + "CORRECTION;NOPE;50.00;motif;;\n"
                + "ACCOUNT_TRANSFER;PC-X;75.00;transfert;true;PC-Y\n"
                + "CORRECTION;PC-X;10.00;motif;false;NOPE\n";

        int imported = service.importAdjustments("UNEP", M, content, "op");

        assertEquals(2, imported);
        assertEquals(2, Adjustment.count("entity = ?1", unep()));
        assertTrue(Adjustment.count("entity = ?1 and autoReverse = true", unep()) >= 1);
    }

    /**
     * The import guards an unknown entity and a missing period.
     */
    @Test
    @TestTransaction
    void guards_unknown_entity_and_period() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.importAdjustments("NOPE", M, HEADER, "op"));
        assertThrows(ResourceNotFoundException.class,
                () -> service.importAdjustments("UNEP", LocalDate.of(2099, 1, 1), HEADER, "op"));
    }
}
