package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.ExternalCode;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.PayeeRule;
import fr.alptis.remuneration.domain.PayeeRuleShare;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ProductionCode;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link PayeeExternalImportService}: payment-rule import with shares, external-code import,
 * on-hold on unknown references and the reject codes of REF-06 and REF-04.
 */
@QuarkusTest
class PayeeExternalImportServiceTest {

    @Inject
    PayeeExternalImportService service;

    /** Returns the seeded Alptis entity. */
    private LegalEntity alptis() {
        return LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
    }

    /** Creates a distributor with the given partner code. */
    private Distributor distributor(String code) {
        Distributor d = new Distributor();
        d.partnerCode = code;
        d.legalName = code;
        d.persist();
        return d;
    }

    /** Creates a production code for Alptis. */
    private void productionCode(String code) {
        ProductionCode pc = new ProductionCode();
        pc.entity = alptis();
        pc.code = code;
        pc.persist();
    }

    /**
     * Loads a two-share rule, holds one whose beneficiary is unknown, rejects a missing perimeter,
     * a bad frequency, a bad share type and a sum different from 1.
     */
    @Test
    @TestTransaction
    void imports_payee_rules() {
        distributor("P1");
        DistributorGroup g = new DistributorGroup();
        g.code = "G1";
        g.label = "Groupe";
        g.persist();
        productionCode("61723");
        ProductRange range = new ProductRange();
        range.code = "SANTE";
        range.label = "Santé";
        range.persist();

        String content = "entity_code;production_code;partner_code;range_code;payment_frequency;valid_from;valid_to;shares\n"
                + "ALPTIS_ASSURANCES;61723;P1;SANTE;MONTHLY;2026-01-01;;DISTRIBUTOR:P1:0.9|GROUP:G1:0.1\n"
                + "ALPTIS_ASSURANCES;;P1;;QUARTERLY;;;DISTRIBUTOR:P_UNKNOWN:1\n"
                + "ALPTIS_ASSURANCES;;;;MONTHLY;;;DISTRIBUTOR:P1:1\n"
                + "ALPTIS_ASSURANCES;;P1;;WEEKLY;;;DISTRIBUTOR:P1:1\n"
                + "ALPTIS_ASSURANCES;;P1;;MONTHLY;;;BADTYPE:P1:1\n"
                + "ALPTIS_ASSURANCES;;P1;;MONTHLY;;;DISTRIBUTOR:P1:0.5\n";

        ImportBatch batch = service.importPayeeRules("ALPTIS_ASSURANCES", "SESAME", "pr.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(4, batch.linesRejected);
        assertEquals(1, PayeeRule.count());
        assertEquals(2, PayeeRuleShare.count());
    }

    /**
     * Rejects a rule whose validity date is malformed, and one whose share percentage is not a number.
     */
    @Test
    @TestTransaction
    void rejects_bad_date_and_bad_share_amount() {
        distributor("P1");
        String content = "entity_code;partner_code;payment_frequency;valid_from;shares\n"
                + "ALPTIS_ASSURANCES;P1;MONTHLY;not-a-date;DISTRIBUTOR:P1:1\n"
                + "ALPTIS_ASSURANCES;P1;MONTHLY;;DISTRIBUTOR:P1:xx\n";

        ImportBatch batch = service.importPayeeRules("ALPTIS_ASSURANCES", "SESAME", "pr2.csv", content);

        assertEquals(0, batch.linesLoaded);
        assertEquals(2, batch.linesRejected);
    }

    /**
     * Loads an external code on a production code, holds one whose internal object is unknown, and
     * rejects a missing external code and an unknown object type.
     */
    @Test
    @TestTransaction
    void imports_external_codes() {
        productionCode("61723");
        String content = "object_type;internal_code;source_system;external_code\n"
                + "PRODUCTION_CODE;61723;CLEVA;C-61723\n"
                + "PRODUCTION_CODE;99999;CLEVA;C-99999\n"
                + "PRODUCTION_CODE;61723;CLEVA;\n"
                + "MYSTERY;61723;CLEVA;C-X\n";

        ImportBatch batch = service.importExternalCodes("ALPTIS_ASSURANCES", "CLEVA", "ec.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(2, batch.linesRejected);
        assertEquals(1, ExternalCode.count());
    }

    /**
     * Loads an external code on a distributor and one on a group, both shared objects.
     */
    @Test
    @TestTransaction
    void imports_external_codes_for_distributor_and_group() {
        distributor("P1");
        DistributorGroup g = new DistributorGroup();
        g.code = "G1";
        g.label = "Groupe";
        g.persist();
        String content = "object_type;internal_code;source_system;external_code\n"
                + "DISTRIBUTOR;P1;SESAME;S-P1\n"
                + "GROUP;G1;SESAME;S-G1\n";

        ImportBatch batch = service.importExternalCodes("ALPTIS_ASSURANCES", "SESAME", "ec2.csv", content);

        assertEquals(2, batch.linesLoaded);
        assertEquals(2, ExternalCode.count());
    }

    /**
     * Covers the remaining payment-rule branches: the empty-frequency and empty-shares legs, a bad
     * closing date, a malformed share, and the on-hold paths for an unknown production code,
     * distributor, range and beneficiary.
     */
    @Test
    @TestTransaction
    void payee_rule_holds_and_rejects() {
        productionCode("PC1");
        distributor("P1");
        ProductRange range = new ProductRange();
        range.code = "R1";
        range.label = "Santé";
        range.persist();

        String content = "entity_code;production_code;partner_code;range_code;payment_frequency;valid_from;valid_to;shares\n"
                + "ALPTIS_ASSURANCES;PC1;;;;;;DISTRIBUTOR:P1:1\n"
                + "ALPTIS_ASSURANCES;PC1;;;MONTHLY;;;\n"
                + "ALPTIS_ASSURANCES;PC1;;;MONTHLY;;notadate;DISTRIBUTOR:P1:1\n"
                + "ALPTIS_ASSURANCES;PC1;;;MONTHLY;;;DISTRIBUTOR:P1\n"
                + "ALPTIS_ASSURANCES;UNKNOWNPC;;;MONTHLY;;;DISTRIBUTOR:P1:1\n"
                + "ALPTIS_ASSURANCES;;UNKNOWNP;;MONTHLY;;;DISTRIBUTOR:P1:1\n"
                + "ALPTIS_ASSURANCES;PC1;;UNKNOWNR;MONTHLY;;;DISTRIBUTOR:P1:1\n"
                + "ALPTIS_ASSURANCES;PC1;;;MONTHLY;;;DISTRIBUTOR:UNKNOWNP:1\n"
                + "ALPTIS_ASSURANCES;PC1;;;MONTHLY;;;GROUP:UNKNOWNG:1\n";

        ImportBatch batch = service.importPayeeRules("ALPTIS_ASSURANCES", "SESAME", "pr-more.csv", content);

        assertEquals(0, batch.linesLoaded);
        assertEquals(4, batch.linesRejected);
        assertEquals(5, batch.linesOnHold);
    }

    /**
     * Covers the remaining external-code reject legs: a missing object type and a missing internal code.
     */
    @Test
    @TestTransaction
    void external_code_rejects_missing_type_and_code() {
        productionCode("PC1");
        String content = "object_type;internal_code;source_system;external_code\n"
                + ";PC1;CLEVA;C-1\n"
                + "PRODUCTION_CODE;;CLEVA;C-2\n";

        ImportBatch batch = service.importExternalCodes("ALPTIS_ASSURANCES", "CLEVA", "ec-more.csv", content);

        assertEquals(0, batch.linesLoaded);
        assertEquals(2, batch.linesRejected);
    }
}
