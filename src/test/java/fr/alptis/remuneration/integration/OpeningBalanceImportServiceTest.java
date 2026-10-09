package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.AccountEntry;
import fr.alptis.remuneration.domain.AccountEntryType;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.ProductionCode;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the opening-balance history reprise (ING-11): a pivot file opens the migrated accounts at
 * their carried balance, for a production code, a distributor and a group; an unknown beneficiary is
 * held and a malformed line rejected. Runs against UNEP in rolled-back transactions.
 */
@QuarkusTest
class OpeningBalanceImportServiceTest {

    @Inject
    ImportService imports;
    @Inject
    AccountService accounts;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /**
     * The file opens three accounts at their balances, holds the unknown beneficiary and rejects the
     * malformed, missing and bad-type lines.
     */
    @Test
    @TestTransaction
    void opens_accounts_and_reports_the_rejections() {
        Distributor distributor = new Distributor();
        distributor.partnerCode = "D1";
        distributor.legalName = "Cabinet";
        distributor.oriasStatus = OriasStatus.REGISTERED;
        distributor.persist();
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.code = "PC1";
        pc.distributor = distributor;
        pc.persist();
        DistributorGroup group = new DistributorGroup();
        group.code = "G1";
        group.label = "Groupement";
        group.persist();

        String csv = "payee_type;payee_code;opening_balance\n"
                + "PRODUCTION_CODE;PC1;-350.00\n"
                + "DISTRIBUTOR;D1;100.00\n"
                + "GROUP;G1;50.00\n"
                + "DISTRIBUTOR;UNKNOWN;10.00\n"
                + "PRODUCTION_CODE;PC1;abc\n"
                + ";X;10.00\n"
                + "PRODUCTION_CODE;;10.00\n"
                + "PRODUCTION_CODE;PC1;\n"
                + "BADTYPE;X;10.00\n";

        ImportBatch batch = imports.importFile("UNEP", OpeningBalanceImportService.OPENING_BALANCES, "SYS",
                "opening.csv", csv);

        assertEquals(3, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(5, batch.linesRejected);
        assertEquals(3, AccountEntry.count("entryType = ?1", AccountEntryType.OPENING_BALANCE));

        DistributorAccount account = accounts.accountFor(unep(), PayeeType.PRODUCTION_CODE, pc.getId());
        assertEquals(0, new BigDecimal("-350.00").compareTo(accounts.balance(account)));
    }
}
