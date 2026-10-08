package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PortfolioTransfer;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.ReplacementOrder;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link TransferImportService}: portfolio transfers and replacement orders, with on-hold on
 * unknown references and reject codes (REF-09, INT-20).
 */
@QuarkusTest
class TransferImportServiceTest {

    @Inject
    TransferImportService service;

    private LegalEntity alptis() {
        return LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
    }

    private Distributor distributor(String code) {
        Distributor d = new Distributor();
        d.partnerCode = code;
        d.legalName = code;
        d.persist();
        return d;
    }

    /**
     * Loads a valid transfer, holds one with an unknown receiver, rejects one missing its effect date.
     */
    @Test
    @TestTransaction
    void imports_portfolio_transfers() {
        distributor("P1");
        distributor("P2");
        String content = "entity_code;from_partner_code;to_partner_code;effect_date;advance_debt_handling\n"
                + "ALPTIS_ASSURANCES;P1;P2;2026-07-01;TAKEN_OVER\n"
                + "ALPTIS_ASSURANCES;P1;P_UNKNOWN;2026-07-01;TAKEN_OVER\n"
                + "ALPTIS_ASSURANCES;P1;P2;;TAKEN_OVER\n";

        ImportBatch batch = service.importPortfolioTransfers("ALPTIS_ASSURANCES", "SESAME", "pt.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(1, batch.linesRejected);
        assertEquals(1, PortfolioTransfer.count());
    }

    /**
     * Loads a replacement order and holds one whose contract is unknown.
     */
    @Test
    @TestTransaction
    void imports_replacement_orders() {
        LegalEntity entity = alptis();
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "CLEVA";
        contract.contractNumber = "AD-1";
        contract.persist();
        for (String code : new String[]{"61723", "61999"}) {
            ProductionCode pc = new ProductionCode();
            pc.entity = entity;
            pc.code = code;
            pc.persist();
        }
        String content = "entity_code;source_system;contract_number;from_production_code;to_production_code;"
                + "received_date;effect_date\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-1;61723;61999;2026-06-01;2026-07-01\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-UNKNOWN;61723;61999;2026-06-01;2026-07-01\n";

        ImportBatch batch = service.importReplacementOrders("ALPTIS_ASSURANCES", "CLEVA", "ro.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(1, ReplacementOrder.count());
    }

    /**
     * Exercises every validation leg, the type errors, the unknown-reference legs and the
     * optional-field branches of both transfer files.
     */
    @Test
    @TestTransaction
    void covers_all_branches() {
        distributor("P1");
        distributor("P2");
        LegalEntity entity = alptis();
        Contract contract = new Contract();
        contract.entity = entity;
        contract.sourceSystem = "CLEVA";
        contract.contractNumber = "AD-1";
        contract.persist();
        for (String code : new String[]{"61723", "61999"}) {
            ProductionCode pc = new ProductionCode();
            pc.entity = entity;
            pc.code = code;
            pc.persist();
        }

        String transfers = "entity_code;from_partner_code;to_partner_code;effect_date;advance_debt_handling;scope\n"
                + "ALPTIS_ASSURANCES;P1;P2;2026-07-01;TAKEN_OVER;{}\n"
                + "ALPTIS_ASSURANCES;PX;P2;2026-07-01;TAKEN_OVER;\n"
                + "ALPTIS_ASSURANCES;;P2;2026-07-01;TAKEN_OVER;\n"
                + "ALPTIS_ASSURANCES;P1;;2026-07-01;TAKEN_OVER;\n"
                + "ALPTIS_ASSURANCES;P1;P2;2026-07-01;;\n"
                + "ALPTIS_ASSURANCES;P1;P2;notadate;TAKEN_OVER;\n"
                + "ALPTIS_ASSURANCES;P1;P2;2026-07-01;BOGUS;\n";
        ImportBatch pt = service.importPortfolioTransfers("ALPTIS_ASSURANCES", "SESAME", "pt-all.csv", transfers);
        assertEquals(1, pt.linesLoaded);
        assertEquals(1, pt.linesOnHold);
        assertEquals(5, pt.linesRejected);

        String orders = "entity_code;source_system;contract_number;from_production_code;to_production_code;received_date;effect_date\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-1;61723;61999;;2026-07-01\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-1;61723;NOPE;2026-06-01;2026-07-01\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-1;NOPE;61999;2026-06-01;2026-07-01\n"
                + "ALPTIS_ASSURANCES;CLEVA;;61723;61999;;2026-07-01\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-1;;61999;;2026-07-01\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-1;61723;;;2026-07-01\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-1;61723;61999;;\n"
                + "ALPTIS_ASSURANCES;CLEVA;AD-1;61723;61999;;notadate\n";
        ImportBatch ro = service.importReplacementOrders("ALPTIS_ASSURANCES", "CLEVA", "ro-all.csv", orders);
        assertEquals(1, ro.linesLoaded);
        assertEquals(2, ro.linesOnHold);
        assertEquals(5, ro.linesRejected);
    }
}
