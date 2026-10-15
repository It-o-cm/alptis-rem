package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.QualityIndicator;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link AccountQualityImportService}: IBAN control and holder resolution for bank accounts,
 * and distributor resolution for quality indicators.
 */
@QuarkusTest
class AccountQualityImportServiceTest {

    @Inject
    AccountQualityImportService service;

    private void seedDistributor() {
        Distributor distributor = new Distributor();
        distributor.partnerCode = "P13897";
        distributor.legalName = "Cabinet Martin";
        distributor.persist();
    }

    /**
     * Validates the ISO 7064 mod-97 IBAN check.
     */
    @Test
    void validates_iban_structure() {
        assertTrue(AccountQualityImportService.validIban("FR7630006000011234567890189"));
        assertFalse(AccountQualityImportService.validIban("FR7630006000011234567890188"));
        assertFalse(AccountQualityImportService.validIban("SHORT"));
        // Too long (over 34 characters).
        assertFalse(AccountQualityImportService.validIban("FR76300060000112345678901890000000000"));
        // Contains a non-alphanumeric character.
        assertFalse(AccountQualityImportService.validIban("FR7630006000011234567890-8"));
    }

    /**
     * Loads a valid bank account, rejects a malformed IBAN and holds an unknown holder.
     */
    @Test
    @TestTransaction
    void imports_bank_accounts() {
        seedDistributor();
        String content = "entity_code;holder_type;holder_code;iban;bic;holder_name;valid_from\n"
                + "ALPTIS_ASSURANCES;DISTRIBUTOR;P13897;FR7630006000011234567890189;AGRIFRPP;Cabinet Martin;2026-01-01\n"
                + "ALPTIS_ASSURANCES;DISTRIBUTOR;P13897;INVALID_IBAN;;Cabinet Martin;2026-01-01\n"
                + "ALPTIS_ASSURANCES;DISTRIBUTOR;P_UNKNOWN;FR7630006000011234567890189;;Autre;2026-01-01\n";

        ImportBatch batch = service.importBankAccounts("ALPTIS_ASSURANCES", "CLEVA", "bank.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesRejected);
        assertEquals(1, batch.linesOnHold);
        assertEquals(1, BankAccount.count());
    }

    /**
     * Loads a quality indicator and holds one for an unknown distributor.
     */
    @Test
    @TestTransaction
    void imports_quality_indicators() {
        seedDistributor();
        String content = "entity_code;partner_code;indicator_type;range_code;period_from;period_to;value\n"
                + "ALPTIS_ASSURANCES;P13897;UNPAID_RATE;;2026-01-01;2026-01-31;0.06\n"
                + "ALPTIS_ASSURANCES;P_UNKNOWN;UNPAID_RATE;;2026-01-01;2026-01-31;0.02\n";

        ImportBatch batch = service.importQualityIndicators("ALPTIS_ASSURANCES", "SESAME", "qi.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        Distributor distributor = Distributor.find("partnerCode", "P13897").firstResult();
        QualityIndicator indicator = QualityIndicator.find("distributor = ?1", distributor).firstResult();
        assertEquals(0, indicator.value.compareTo(new java.math.BigDecimal("0.06")));
    }

    /**
     * Exercises every validation leg, the holder-type resolution switch and the optional-field
     * branches of both bank accounts and quality indicators.
     */
    @Test
    @TestTransaction
    void covers_all_branches() {
        seedDistributor();
        fr.alptis.remuneration.domain.LegalEntity entity =
                fr.alptis.remuneration.domain.LegalEntity.find("code", "ALPTIS_ASSURANCES").firstResult();
        fr.alptis.remuneration.domain.ProductionCode pc = new fr.alptis.remuneration.domain.ProductionCode();
        pc.entity = entity;
        pc.code = "PC1";
        pc.persist();
        fr.alptis.remuneration.domain.DistributorGroup group = new fr.alptis.remuneration.domain.DistributorGroup();
        group.code = "G1";
        group.label = "Groupe";
        group.persist();
        fr.alptis.remuneration.domain.ProductRange range = new fr.alptis.remuneration.domain.ProductRange();
        range.code = "R1";
        range.label = "Santé";
        range.persist();

        String iban = "FR7630006000011234567890189";
        String bank = "entity_code;holder_type;holder_code;iban;bic;holder_name;valid_from;valid_to\n"
                + "ALPTIS_ASSURANCES;DISTRIBUTOR;P13897;" + iban + ";;Cabinet;2026-01-01;2026-12-31\n"
                + "ALPTIS_ASSURANCES;PRODUCTION_CODE;PC1;" + iban + ";AGRIFRPP;Code;2026-01-01;\n"
                + "ALPTIS_ASSURANCES;GROUP;G1;" + iban + ";AGRIFRPP;Groupe;2026-01-01;\n"
                + "ALPTIS_ASSURANCES;;P13897;" + iban + ";;Nom;2026-01-01;\n"
                + "ALPTIS_ASSURANCES;DISTRIBUTOR;;" + iban + ";;Nom;2026-01-01;\n"
                + "ALPTIS_ASSURANCES;DISTRIBUTOR;P13897;;;Nom;2026-01-01;\n"
                + "ALPTIS_ASSURANCES;DISTRIBUTOR;P13897;" + iban + ";;;2026-01-01;\n"
                + "ALPTIS_ASSURANCES;DISTRIBUTOR;P13897;" + iban + ";;Nom;;\n"
                + "ALPTIS_ASSURANCES;BOGUS;P13897;" + iban + ";;Nom;2026-01-01;\n"
                + "ALPTIS_ASSURANCES;DISTRIBUTOR;P13897;" + iban + ";;Nom;notadate;\n";
        ImportBatch bankBatch = service.importBankAccounts("ALPTIS_ASSURANCES", "CLEVA", "bank-all.csv", bank);
        assertEquals(3, bankBatch.linesLoaded);
        assertEquals(7, bankBatch.linesRejected);

        String qi = "entity_code;partner_code;indicator_type;range_code;period_from;period_to;value;source\n"
                + "ALPTIS_ASSURANCES;P13897;COMPLAINT_RATE;R1;2026-01-01;2026-01-31;0.06;AUDIT\n"
                + "ALPTIS_ASSURANCES;;COMPLAINT_RATE;;2026-01-01;2026-01-31;0.06;\n"
                + "ALPTIS_ASSURANCES;P13897;;;2026-01-01;2026-01-31;0.06;\n"
                + "ALPTIS_ASSURANCES;P13897;COMPLAINT_RATE;;;2026-01-31;0.06;\n"
                + "ALPTIS_ASSURANCES;P13897;COMPLAINT_RATE;;2026-01-01;;0.06;\n"
                + "ALPTIS_ASSURANCES;P13897;COMPLAINT_RATE;;2026-01-01;2026-01-31;;\n"
                + "ALPTIS_ASSURANCES;P13897;BOGUS;;2026-01-01;2026-01-31;0.06;\n"
                + "ALPTIS_ASSURANCES;P13897;COMPLAINT_RATE;;2026-01-01;notadate;0.06;\n"
                + "ALPTIS_ASSURANCES;P13897;COMPLAINT_RATE;;2026-01-01;2026-01-31;xx;\n";
        ImportBatch qiBatch = service.importQualityIndicators("ALPTIS_ASSURANCES", "SESAME", "qi-all.csv", qi);
        assertEquals(1, qiBatch.linesLoaded);
        assertEquals(8, qiBatch.linesRejected);
    }
}
