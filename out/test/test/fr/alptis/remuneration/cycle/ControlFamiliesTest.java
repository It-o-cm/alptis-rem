package fr.alptis.remuneration.cycle;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementStatus;
import fr.alptis.remuneration.domain.AdvancePosition;
import fr.alptis.remuneration.domain.Agreement;
import fr.alptis.remuneration.domain.AgreementType;
import fr.alptis.remuneration.domain.BankAccount;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ControlDefinition;
import fr.alptis.remuneration.domain.ControlFamily;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.EndReason;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.QualityIndicatorType;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.SignatureStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Branch-complete tests of the ten control families delivered for CTL-02 (section 8.6). Each test
 * isolates one control by clearing the entity's seeded catalogue and creating just the control under
 * test, then sets up both offending and clean objects to exercise every branch. Runs against UNEP,
 * inside rolled-back transactions on the clean test database.
 */
@QuarkusTest
class ControlFamiliesTest {

    @Inject
    ControlService service;
    @Inject
    AccountService accounts;

    private static final LocalDate M = LocalDate.of(2026, 3, 1);
    private int seq;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Creates a period of a month in the calculated state. */
    private static Period period(LocalDate month) {
        Period period = new Period();
        period.entity = unep();
        period.periodMonth = month;
        period.state = PeriodState.CALCULATED;
        period.persist();
        return period;
    }

    /** Clears UNEP's catalogue and creates the single active control under test. */
    private static ControlDefinition only(String code, ControlFamily family, ControlSeverity severity,
                                          String parameters) {
        ControlResult.delete("control.entity = ?1", unep());
        ControlDefinition.delete("entity = ?1", unep());
        ControlDefinition def = new ControlDefinition();
        def.entity = unep();
        def.code = code;
        def.label = code;
        def.family = family;
        def.severity = severity;
        def.parameters = parameters;
        def.active = Boolean.TRUE;
        def.persist();
        return def;
    }

    /** Creates an event eligible to the period. */
    private BusinessEvent event(Period period, String source, LocalDate date, String basis, ProcessingStatus status,
                                HoldReason hold, Coverage coverage) {
        BusinessEvent e = new BusinessEvent();
        e.entity = unep();
        e.sourceSystem = source;
        e.sourceKey = "K" + (seq++);
        e.eventType = EventType.PREMIUM_COLLECTED;
        e.eventDate = date;
        e.basisAmount = basis == null ? null : new BigDecimal(basis);
        e.processingStatus = status;
        e.holdReason = hold;
        e.coverage = coverage;
        e.eligiblePeriod = period;
        e.persist();
        return e;
    }

    /** Creates a product-less coverage attached to a UNEP contract. */
    private Coverage coverage(String number) {
        Product p = Product.find("code", "FAM_CTL").firstResult();
        if (p == null) {
            p = new Product();
            p.entity = unep();
            p.code = "FAM_CTL";
            p.label = "p";
            p.persist();
        }
        Contract c = new Contract();
        c.entity = unep();
        c.sourceSystem = "SYS";
        c.contractNumber = number + (seq++);
        c.product = p;
        c.persist();
        Coverage cov = new Coverage();
        cov.contract = c;
        cov.coverageCode = number + "-C";
        cov.effectDate = M;
        cov.persist();
        return cov;
    }

    /** Creates a distributor. */
    private Distributor distributor(String code) {
        Distributor d = new Distributor();
        d.partnerCode = code + (seq++);
        d.legalName = code;
        d.persist();
        return d;
    }

    /** Creates a membership of a distributor in UNEP. */
    private static void membership(Distributor distributor, PartnerStatus status, LocalDate from, LocalDate to) {
        DistributorEntity m = new DistributorEntity();
        m.entity = unep();
        m.distributor = distributor;
        m.status = status;
        m.validFrom = from;
        m.validTo = to;
        m.persist();
    }

    /** Creates a production code of a distributor. */
    private ProductionCode productionCode(Distributor distributor) {
        ProductionCode pc = new ProductionCode();
        pc.entity = unep();
        pc.distributor = distributor;
        pc.code = "PC" + (seq++);
        pc.status = PartnerStatus.ACTIVE;
        pc.persist();
        return pc;
    }

    /** Assigns a production code to a coverage over a validity window. */
    private static void assignment(Coverage coverage, ProductionCode code, LocalDate from, LocalDate to) {
        CoverageAssignment a = new CoverageAssignment();
        a.coverage = coverage;
        a.productionCode = code;
        a.validFrom = from;
        a.validTo = to;
        a.persist();
    }

    /** Creates a commission line on a period with a nature and amount. */
    private CommissionLine line(Period period, LineNature nature, String amount) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.period = period;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = nature;
        line.amount = new BigDecimal(amount);
        line.persist();
        return line;
    }

    /** Returns or creates a distributor account and posts an amount to it in a period. */
    private DistributorAccount account(Distributor distributor, Period period, String amount) {
        DistributorAccount acct = accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, distributor.getId());
        CommissionLine line = line(period, LineNature.LINEAR, amount);
        line.account = acct;
        return acct;
    }

    /** Creates a bank account for a distributor account. */
    private static void bank(DistributorAccount account, String iban, LocalDate from, LocalDate to) {
        BankAccount b = new BankAccount();
        b.entity = unep();
        b.holderType = account.payeeType;
        b.holderId = account.payeeId;
        b.iban = iban;
        b.validFrom = from;
        b.validTo = to;
        b.persist();
    }

    /** The number of anomalies recorded for the period. */
    private static long anomalies(Period period) {
        return ControlResult.count("period = ?1", period);
    }

    // ----------------------------------------------------------------------------------------------

    /**
     * Completeness: a missing expected flow is flagged, a received one is not; the receipt check sees
     * through a null date, a wrong year and a wrong month.
     */
    @Test
    @TestTransaction
    void completeness_flows() {
        Period clean = period(M);
        only("COMP-FLOWS", ControlFamily.COMPLETENESS, ControlSeverity.BLOCKING, null);
        importBatch("coverages", OffsetDateTime.of(2026, 3, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        importBatch("events", OffsetDateTime.of(2026, 3, 11, 0, 0, 0, 0, ZoneOffset.UTC));
        assertEquals(0, service.run("UNEP", M));

        // One custom flow, received only through a null date, a wrong year and a wrong month: missing.
        Period missing = period(LocalDate.of(2026, 4, 1));
        only("COMP-FLOWS", ControlFamily.COMPLETENESS, ControlSeverity.BLOCKING, "{\"flows\":[\"x\"]}");
        importBatch("x", null);
        importBatch("x", OffsetDateTime.of(2025, 4, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        importBatch("x", OffsetDateTime.of(2026, 3, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        assertEquals(1, service.run("UNEP", LocalDate.of(2026, 4, 1)));
        assertEquals(1, anomalies(missing));
    }

    /** Creates an import batch received at an instant for UNEP. */
    private void importBatch(String flow, OffsetDateTime receivedAt) {
        ImportBatch batch = new ImportBatch();
        batch.entity = unep();
        batch.flowCode = flow;
        batch.sourceSystem = "SYS";
        batch.fileName = "f" + (seq++);
        batch.fileHash = "H" + (seq++);
        batch.receivedAt = receivedAt;
        batch.persist();
    }

    /**
     * Completeness: a source whose event count swings beyond the threshold is flagged, a stable one and
     * a brand-new one are not; with no previous month nothing runs.
     */
    @Test
    @TestTransaction
    void completeness_volume() {
        Period previous = period(LocalDate.of(2026, 2, 1));
        Period current = period(M);
        only("COMP-VOLUME", ControlFamily.COMPLETENESS, ControlSeverity.WARNING, "{\"deviation\":0.50}");
        // SRC-A: 1 -> 3 (deviation 200 % > 50 %): flagged.
        event(previous, "SRC-A", M, "100", ProcessingStatus.PROCESSED, null, null);
        event(current, "SRC-A", M, "100", ProcessingStatus.PROCESSED, null, null);
        event(current, "SRC-A", M, "100", ProcessingStatus.PROCESSED, null, null);
        event(current, "SRC-A", M, "100", ProcessingStatus.PROCESSED, null, null);
        // SRC-B: 2 -> 2 (stable): not flagged.
        event(previous, "SRC-B", M, "100", ProcessingStatus.PROCESSED, null, null);
        event(previous, "SRC-B", M, "100", ProcessingStatus.PROCESSED, null, null);
        event(current, "SRC-B", M, "100", ProcessingStatus.PROCESSED, null, null);
        event(current, "SRC-B", M, "100", ProcessingStatus.PROCESSED, null, null);
        // SRC-C: new source (no previous): not flagged (before == 0). Null source tolerated.
        event(current, null, M, "100", ProcessingStatus.PROCESSED, null, null);
        assertEquals(1, service.run("UNEP", M));

        // With no previous month the control is silent.
        Period alone = period(LocalDate.of(2026, 5, 1));
        event(alone, "SRC-A", M, "100", ProcessingStatus.PROCESSED, null, null);
        assertEquals(0, service.run("UNEP", LocalDate.of(2026, 5, 1)));
    }

    /**
     * Data quality: an abnormally high premium is flagged when a maximum is set, while null and
     * non-positive premiums are always flagged and a normal one is tolerated; with no maximum the upper
     * bound is off.
     */
    @Test
    @TestTransaction
    void data_quality_premium_abnormal() {
        Period p = period(M);
        only("DATA-PREMIUM", ControlFamily.DATA_QUALITY, ControlSeverity.WARNING, "{\"maxPremium\":1000}");
        event(p, "S", M, null, ProcessingStatus.PROCESSED, null, null);
        event(p, "S", M, "0", ProcessingStatus.PROCESSED, null, null);
        event(p, "S", M, "5000", ProcessingStatus.PROCESSED, null, null);
        event(p, "S", M, "500", ProcessingStatus.PROCESSED, null, null);
        assertEquals(3, service.run("UNEP", M));

        // Without a maximum, only null and non-positive premiums are flagged.
        Period q = period(LocalDate.of(2026, 4, 1));
        only("DATA-PREMIUM", ControlFamily.DATA_QUALITY, ControlSeverity.WARNING, null);
        event(q, "S", M, "5000", ProcessingStatus.PROCESSED, null, null);
        assertEquals(0, service.run("UNEP", LocalDate.of(2026, 4, 1)));
    }

    /**
     * Data quality: a coverage with no assignment covering the month is flagged, one with an assignment
     * is not.
     */
    @Test
    @TestTransaction
    void data_quality_no_distributor() {
        Period p = period(M);
        only("DATA-NO-DIST", ControlFamily.DATA_QUALITY, ControlSeverity.WARNING, null);
        // Orphan coverage with no assignment at all: flagged.
        Coverage orphan = coverage("ORPH");
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, orphan);
        // Assigned coverage with a dated window and a null-dated assignment: active, not flagged.
        Coverage assigned = coverage("ASSG");
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, assigned);
        assignment(assigned, productionCode(distributor("D")), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assignment(assigned, productionCode(distributor("D2")), null, null);
        // Coverage whose only assignments are out of window (one starts too late, one ends too early):
        // flagged.
        Coverage outWin = coverage("OUTWIN");
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, outWin);
        assignment(outWin, productionCode(distributor("D3")), LocalDate.of(2026, 6, 1), null);
        assignment(outWin, productionCode(distributor("D4")), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1));
        // A coverage-less event is ignored.
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, null);
        assertEquals(2, service.run("UNEP", M));
    }

    /**
     * Data quality: an assignment pointing to no distributor, or to one inactive in the entity, is
     * flagged; an active one is not. The membership check sees through a late start, an early end and a
     * non-active status.
     */
    @Test
    @TestTransaction
    void data_quality_inactive_distributor() {
        Period p = period(M);
        only("DATA-DIST-INACTIVE", ControlFamily.DATA_QUALITY, ControlSeverity.WARNING, null);
        // Active distributor (a null-dated membership and a fully dated one): not flagged.
        Coverage ok = coverage("OK");
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, ok);
        Distributor active = distributor("ACT");
        membership(active, PartnerStatus.ACTIVE, null, null);
        membership(active, PartnerStatus.ACTIVE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assignment(ok, productionCode(active), LocalDate.of(2026, 1, 1), null);
        // Inactive distributor (terminated, and a membership that starts too late and one that ends too
        // early): flagged as inactive.
        Coverage ko = coverage("KO");
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, ko);
        Distributor inactive = distributor("INA");
        membership(inactive, PartnerStatus.TERMINATED, LocalDate.of(2026, 1, 1), null);
        membership(inactive, PartnerStatus.ACTIVE, LocalDate.of(2026, 6, 1), null);
        membership(inactive, PartnerStatus.ACTIVE, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1));
        // A suspended membership inside a dated, still-open window (future end date): it is reached and
        // its end-date and start-date comparisons both pass, but the non-active status rejects it.
        membership(inactive, PartnerStatus.SUSPENDED, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 12, 31));
        assignment(ko, productionCode(inactive), LocalDate.of(2026, 1, 1), null);
        // Coverage whose assignment carries no production code: flagged as unknown.
        Coverage noPc = coverage("NOPC");
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, noPc);
        assignment(noPc, null, LocalDate.of(2026, 1, 1), null);
        assertEquals(2, service.run("UNEP", M));
    }

    /**
     * Data quality: an account due a payment but without a valid IBAN is flagged; one with an IBAN, and
     * one with nothing to pay, are not. The IBAN check sees through a late start, an early end and a
     * blank value.
     */
    @Test
    @TestTransaction
    void data_quality_iban() {
        Period p = period(M);
        only("DATA-IBAN", ControlFamily.DATA_QUALITY, ControlSeverity.WARNING, null);
        // Creditor without IBAN (only blank / out-of-window bank rows): flagged.
        DistributorAccount noIban = account(distributor("NOIBAN"), p, "100");
        bank(noIban, "  ", null, null);
        bank(noIban, "  ", LocalDate.of(2026, 1, 1), null);
        bank(noIban, "FR123", LocalDate.of(2026, 6, 1), null);
        bank(noIban, "FR999", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1));
        // Creditor with a valid IBAN within a dated window: not flagged.
        DistributorAccount withIban = account(distributor("IBANOK"), p, "100");
        bank(withIban, "FR7612345", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        // Nothing to pay (zero balance): not flagged.
        account(distributor("EMPTY"), p, "0");
        assertEquals(1, service.run("UNEP", M));
    }

    /**
     * Calculation: events held for a rule, a scale or a grid reason are flagged by their respective
     * controls.
     */
    @Test
    @TestTransaction
    void calculation_hold_reasons() {
        Period p = period(M);
        only("CALC-RATE-OFF-SCALE", ControlFamily.CALCULATION, ControlSeverity.WARNING, null);
        event(p, "S", M, "100", ProcessingStatus.ON_HOLD, HoldReason.OUT_OF_BOUNDS, null);
        event(p, "S", M, "100", ProcessingStatus.ON_HOLD, HoldReason.NO_RULE, null);
        assertEquals(1, service.run("UNEP", M));

        only("DATA-RATE-OFF-GRID", ControlFamily.DATA_QUALITY, ControlSeverity.WARNING, null);
        event(p, "S", M, "100", ProcessingStatus.ON_HOLD, HoldReason.RATE_OUT_OF_GRID, null);
        assertEquals(1, service.run("UNEP", M));
    }

    /**
     * Calculation: a nature whose total swings from the previous month or the same month of the
     * previous year beyond the threshold is flagged; a stable one is not, and a null or zero reference
     * never triggers.
     */
    @Test
    @TestTransaction
    void calculation_nature_total() {
        Period lastYear = period(LocalDate.of(2025, 3, 1));
        Period lastMonth = period(LocalDate.of(2026, 2, 1));
        Period current = period(M);
        only("CALC-NATURE-TOTAL", ControlFamily.CALCULATION, ControlSeverity.WARNING, "{\"natureDeviation\":0.30}");
        // LINEAR: 100 last month, 100 last year, 500 now (off month and off year): flagged once.
        line(lastMonth, LineNature.LINEAR, "100");
        line(lastYear, LineNature.LINEAR, "100");
        line(current, LineNature.LINEAR, "500");
        // RCD: stable last month (100 -> 100) but off last year (10 -> 100): flagged (off-year leg only).
        line(lastMonth, LineNature.RCD, "100");
        line(lastYear, LineNature.RCD, "10");
        line(current, LineNature.RCD, "100");
        // ACQUISITION: stable 100 -> 100: not flagged.
        line(lastMonth, LineNature.ACQUISITION, "100");
        line(current, LineNature.ACQUISITION, "100");
        // SERVICING: no reference at all (both null): not flagged.
        line(current, LineNature.SERVICING, "999");
        // COMPLEMENT: last month total zero (a zero reference never triggers): not flagged.
        line(lastMonth, LineNature.COMPLEMENT, "0");
        line(current, LineNature.COMPLEMENT, "100");
        // A line with a null amount and one with a null nature are both ignored by the totals.
        line(current, LineNature.LINEAR, "0").amount = null;
        line(current, LineNature.LINEAR, "5").nature = null;
        assertEquals(2, service.run("UNEP", M));
    }

    /**
     * Calculation: a distributor whose total varies beyond the threshold is flagged; a stable one, a
     * brand-new one and one whose previous total was zero are not.
     */
    @Test
    @TestTransaction
    void calculation_distributor_variation() {
        Period previous = period(LocalDate.of(2026, 2, 1));
        Period current = period(M);
        only("CALC-DIST-VARIATION", ControlFamily.CALCULATION, ControlSeverity.WARNING, "{\"variation\":0.30}");
        Distributor swinging = distributor("SW");
        distributorLine(previous, swinging, "100", "1000");
        distributorLine(current, swinging, "200", "1000");
        Distributor stable = distributor("ST");
        distributorLine(previous, stable, "100", "1000");
        distributorLine(current, stable, "100", "1000");
        Distributor fresh = distributor("FR");
        distributorLine(current, fresh, "100", "1000");
        Distributor wasZero = distributor("ZE");
        distributorLine(previous, wasZero, "0", "1000");
        distributorLine(current, wasZero, "100", "1000");
        // A line with a null amount is ignored by the totals.
        distributorLine(current, distributor("NA"), "0", "1000").amount = null;
        assertEquals(1, service.run("UNEP", M));
    }

    /** Creates a commission line on a period, carrying an origin distributor, an amount and a basis. */
    private CommissionLine distributorLine(Period period, Distributor distributor, String amount, String basis) {
        CommissionLine line = line(period, LineNature.LINEAR, amount);
        line.originDistributor = distributor;
        line.basisAmount = new BigDecimal(basis);
        return line;
    }

    /**
     * Calculation: a distributor whose average rate breaks with its previous month beyond the threshold
     * is flagged; a stable one, a new one and one whose previous basis was zero are not.
     */
    @Test
    @TestTransaction
    void calculation_average_rate() {
        Period previous = period(LocalDate.of(2026, 2, 1));
        Period current = period(M);
        only("CALC-AVG-RATE", ControlFamily.CALCULATION, ControlSeverity.WARNING, "{\"rateBreak\":0.20}");
        // Breaking: 10 % last month, 20 % now.
        Distributor breaking = distributor("BR");
        distributorLine(previous, breaking, "100", "1000");
        distributorLine(current, breaking, "200", "1000");
        // Stable: 10 % both months.
        Distributor stable = distributor("SR");
        distributorLine(previous, stable, "100", "1000");
        distributorLine(current, stable, "100", "1000");
        // Previous basis zero: no previous rate computed.
        Distributor zeroBasis = distributor("ZB");
        distributorLine(previous, zeroBasis, "0", "0");
        distributorLine(current, zeroBasis, "100", "1000");
        // A line with a null amount and one with a null basis are ignored by the average.
        distributorLine(current, distributor("NAMT"), "0", "1000").amount = null;
        distributorLine(current, distributor("NBAS"), "100", "0").basisAmount = null;
        assertEquals(1, service.run("UNEP", M));
    }

    /**
     * Calculation: the top-distributor review lists up to the configured number of distributors by
     * total, in descending order, and stops at the cap.
     */
    @Test
    @TestTransaction
    void calculation_top_review() {
        Period current = period(M);
        only("CALC-TOP-20", ControlFamily.CALCULATION, ControlSeverity.INFO, "{\"top\":2}");
        distributorLine(current, distributor("A"), "300", "1000");
        distributorLine(current, distributor("B"), "200", "1000");
        distributorLine(current, distributor("C"), "100", "1000");
        assertEquals(2, service.run("UNEP", M));
    }

    /**
     * Advance: an active agreement without any computed position is flagged by the no-discount control;
     * one with a position is not.
     */
    @Test
    @TestTransaction
    void advance_no_discount() {
        Period p = period(M);
        only("ADV-NO-DISCOUNT", ControlFamily.ADVANCE, ControlSeverity.WARNING, null);
        agreement(distributor("NODISC"), null);
        AdvanceAgreement withPos = agreement(distributor("HASDISC"), null);
        position(withPos, coverage("CP"), "500", "0");
        // An agreement without a distributor is skipped by the no-discount control.
        agreement(null, null);
        assertEquals(1, service.run("UNEP", M));
    }

    /** Creates a valid advance agreement for a distributor. */
    private AdvanceAgreement agreement(Distributor distributor, AdvanceAgreementStatus status) {
        AdvanceAgreement a = new AdvanceAgreement();
        a.entity = unep();
        a.distributor = distributor;
        a.status = status == null ? AdvanceAgreementStatus.VALID : status;
        a.persist();
        return a;
    }

    /** Creates an advance position under an agreement with an advanced and offset amount. */
    private void position(AdvanceAgreement agreement, Coverage coverage, String advanced, String offset) {
        AdvancePosition pos = new AdvancePosition();
        pos.advanceAgreement = agreement;
        pos.coverage = coverage;
        pos.advancedLinear = new BigDecimal(advanced);
        pos.offsetLinear = new BigDecimal(offset);
        pos.persist();
    }

    /**
     * Advance: an agreement whose distributor is not an active member is flagged by the no-member
     * control; one whose distributor is active is not.
     */
    @Test
    @TestTransaction
    void advance_no_member() {
        Period p = period(M);
        only("ADV-NO-MEMBER", ControlFamily.ADVANCE, ControlSeverity.WARNING, null);
        agreement(distributor("ORPHAN"), null);
        Distributor active = distributor("MEMBER");
        membership(active, PartnerStatus.ACTIVE, LocalDate.of(2026, 1, 1), null);
        agreement(active, null);
        // An agreement without a distributor counts as having no member: flagged.
        agreement(null, null);
        assertEquals(2, service.run("UNEP", M));
    }

    /**
     * Advance: an advance line attached to a payment order is flagged; one without a payment order is
     * not.
     */
    @Test
    @TestTransaction
    void advance_settlement_in_payment() {
        Period p = period(M);
        only("ADV-SETTLE-IN-PAY", ControlFamily.ADVANCE, ControlSeverity.BLOCKING, null);
        PaymentOrder order = paymentOrder(batch(p, "0"), null, "0");
        CommissionLine paid = line(p, LineNature.ADVANCE_ADJUSTMENT, "-50");
        paid.paymentOrder = order;
        line(p, LineNature.ADVANCE, "-50");
        assertEquals(1, service.run("UNEP", M));
    }

    /**
     * Advance: an advance position whose outstanding receivable exceeds the cap is flagged; one under
     * the cap is not, a position of another entity is ignored, and with no cap set the control is
     * silent.
     */
    @Test
    @TestTransaction
    void advance_receivable_cap() {
        Period p = period(M);
        only("ADV-RECEIVABLE-CAP", ControlFamily.ADVANCE, ControlSeverity.WARNING, "{\"cap\":1000}");
        AdvanceAgreement a = agreement(distributor("CAP"), null);
        position(a, coverage("OVER"), "5000", "0");
        position(a, coverage("UNDER"), "500", "0");
        // A position with no coverage, one whose coverage has no contract, and one of another entity are
        // all ignored.
        position(a, null, "9000", "0");
        Coverage noContract = new Coverage();
        noContract.coverageCode = "NC" + (seq++);
        noContract.persist();
        position(a, noContract, "9000", "0");
        LegalEntity other = new LegalEntity();
        other.code = "OTHER_CTL";
        other.name = "Autre";
        other.persist();
        Contract foreign = new Contract();
        foreign.entity = other;
        foreign.sourceSystem = "SYS";
        foreign.contractNumber = "F" + (seq++);
        foreign.persist();
        Coverage foreignCov = new Coverage();
        foreignCov.contract = foreign;
        foreignCov.coverageCode = "FC" + (seq++);
        foreignCov.persist();
        position(a, foreignCov, "9000", "0");
        assertEquals(1, service.run("UNEP", M));

        // With no cap the control does nothing even over a large position.
        Period q = period(LocalDate.of(2026, 4, 1));
        only("ADV-RECEIVABLE-CAP", ControlFamily.ADVANCE, ControlSeverity.WARNING, null);
        assertEquals(0, service.run("UNEP", LocalDate.of(2026, 4, 1)));
    }

    /**
     * Clawback: a coverage ended early without any clawback line is flagged; one with a clawback, one
     * ended late and one ended for another reason are not.
     */
    @Test
    @TestTransaction
    void clawback_missing() {
        Period p = period(M);
        only("CLAWBACK-MISSING", ControlFamily.CLAWBACK, ControlSeverity.WARNING, "{\"earlyMonths\":12}");
        // Early termination, no clawback: flagged.
        Coverage early = coverage("EARLY");
        early.endDate = LocalDate.of(2026, 6, 1);
        early.endReason = EndReason.TERMINATION;
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, early);
        // Early termination but with a clawback line: not flagged.
        Coverage clawed = coverage("CLAWED");
        clawed.endDate = LocalDate.of(2026, 6, 1);
        clawed.endReason = EndReason.TERMINATION;
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, clawed);
        CommissionLine cl = line(p, LineNature.CLAWBACK, "-10");
        cl.coverage = clawed;
        // Late termination (after the window): not flagged.
        Coverage late = coverage("LATE");
        late.endDate = LocalDate.of(2027, 6, 1);
        late.endReason = EndReason.TERMINATION;
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, late);
        // Still active (no end): not flagged.
        Coverage active = coverage("LIVE");
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, active);
        // Ended for another reason (withdrawal, not termination): not flagged.
        Coverage withdrawn = coverage("WD");
        withdrawn.endDate = LocalDate.of(2026, 6, 1);
        withdrawn.endReason = EndReason.WITHDRAWAL;
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, withdrawn);
        // Terminated but with no effect date: not flagged.
        Coverage noEffect = coverage("NOEFF");
        noEffect.effectDate = null;
        noEffect.endDate = LocalDate.of(2026, 6, 1);
        noEffect.endReason = EndReason.TERMINATION;
        event(p, "S", M, "100", ProcessingStatus.PROCESSED, null, noEffect);
        assertEquals(1, service.run("UNEP", M));
    }

    /**
     * Supplement: a supplement line whose rule carries no signed agreement is flagged; one on a signed
     * agreement is not. A missing rule version and an unsigned agreement both count as missing.
     */
    @Test
    @TestTransaction
    void supplement_without_agreement() {
        Period p = period(M);
        only("SUPP-NO-AGREEMENT", ControlFamily.SUPPLEMENT, ControlSeverity.WARNING, null);
        // No rule version at all: flagged.
        line(p, LineNature.RCD, "100");
        // Rule version with an unsigned agreement: flagged.
        line(p, LineNature.RCP, "100").ruleVersion = ruleVersion(agreementRef(SignatureStatus.NOT_SIGNED));
        // Rule version with a signed agreement: not flagged.
        line(p, LineNature.COMPLEMENT, "100").ruleVersion = ruleVersion(agreementRef(SignatureStatus.SIGNED));
        assertEquals(2, service.run("UNEP", M));
    }

    /** Creates an agreement with a signature status. */
    private Agreement agreementRef(SignatureStatus status) {
        Agreement a = new Agreement();
        a.entity = unep();
        a.agreementType = AgreementType.RCD_AMENDMENT;
        a.reference = "AG" + (seq++);
        a.signatureStatus = status;
        a.persist();
        return a;
    }

    /** Creates a rule version carrying an agreement. */
    private RuleVersion ruleVersion(Agreement agreement) {
        Rule rule = new Rule();
        rule.code = "R" + (seq++);
        rule.label = "r";
        rule.family = RuleFamily.LINEAR;
        rule.persist();
        RuleVersion version = new RuleVersion();
        version.rule = rule;
        version.versionNo = 1;
        version.agreement = agreement;
        version.persist();
        return version;
    }

    /**
     * Supplement: a distributor bearing supplement lines but no quality indicator for the period is
     * flagged once; a distributor with an indicator and a line without a distributor are not.
     */
    @Test
    @TestTransaction
    void supplement_missing_indicator() {
        Period p = period(M);
        only("SUPP-MISSING-INDICATOR", ControlFamily.SUPPLEMENT, ControlSeverity.BLOCKING, null);
        Distributor noInd = distributor("NOIND");
        line(p, LineNature.RCD, "100").originDistributor = noInd;
        line(p, LineNature.RCP, "100").originDistributor = noInd;
        Distributor withInd = distributor("WITHIND");
        line(p, LineNature.RCD, "100").originDistributor = withInd;
        indicator(withInd, QualityIndicatorType.COMPLAINT_RATE, "0.01");
        // A supplement line without a distributor is skipped.
        line(p, LineNature.RCD, "100");
        assertEquals(1, service.run("UNEP", M));
    }

    /** Creates a quality indicator for a distributor covering the control month. */
    private void indicator(Distributor distributor, QualityIndicatorType type, String value) {
        QualityIndicator q = new QualityIndicator();
        q.entity = unep();
        q.distributor = distributor;
        q.indicatorType = type;
        q.periodFrom = LocalDate.of(2026, 1, 1);
        q.periodTo = LocalDate.of(2026, 12, 31);
        q.value = new BigDecimal(value);
        q.persist();
    }

    /**
     * Supplement: a quality indicator crossing its amendment-termination threshold is flagged; one
     * below, one with no value and one of a type without a threshold are not.
     */
    @Test
    @TestTransaction
    void supplement_quality_threshold() {
        Period p = period(M);
        only("SUPP-QUALITY-THRESHOLD", ControlFamily.SUPPLEMENT, ControlSeverity.WARNING, null);
        Distributor d = distributor("Q");
        indicator(d, QualityIndicatorType.COMPLAINT_RATE, "0.06");
        indicator(d, QualityIndicatorType.NOT_PURSUED_RATE, "0.15");
        indicator(d, QualityIndicatorType.PORTFOLIO_LAPSE_RATE, "0.25");
        indicator(d, QualityIndicatorType.UNPAID_RATE, "0.02");
        QualityIndicator noValue = new QualityIndicator();
        noValue.entity = unep();
        noValue.distributor = d;
        noValue.indicatorType = QualityIndicatorType.COMPLAINT_RATE;
        noValue.periodFrom = LocalDate.of(2026, 1, 1);
        noValue.periodTo = LocalDate.of(2026, 12, 31);
        noValue.persist();
        indicator(d, QualityIndicatorType.LOSS_RATIO, "0.90");
        assertEquals(3, service.run("UNEP", M));
    }

    /**
     * Account: a debit older than the window is flagged; a recent debit, a creditor account, a
     * production-code account and an account of an unknown distributor are not.
     */
    @Test
    @TestTransaction
    void account_old_debit() {
        Period october = period(LocalDate.of(2025, 10, 1));
        Period november = period(LocalDate.of(2025, 11, 1));
        Period current = period(M);
        only("ACCOUNT-OLD-DEBIT", ControlFamily.ACCOUNT, ControlSeverity.WARNING, "{\"months\":3}");
        // A distributor credited in October then heavily debited in November (before the 2025-12 cutoff):
        // its balance was positive at one month and negative at an earlier-than-cutoff month, so it is
        // flagged as an old debit.
        DistributorAccount oldAcct = account(distributor("OLD"), october, "100");
        line(november, LineNature.LINEAR, "-300").account = oldAcct;
        // Debtor only this month: not flagged.
        account(distributor("NEW"), current, "-100");
        // Creditor: not flagged.
        account(distributor("CRED"), current, "100");
        // Production-code account: skipped.
        accounts.accountFor(unep(), PayeeType.PRODUCTION_CODE, 1L);
        // Account of an unknown distributor id: skipped.
        DistributorAccount ghost = accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, 999_999L);
        CommissionLine ghostLine = line(current, LineNature.LINEAR, "-100");
        ghostLine.account = ghost;
        assertEquals(1, service.run("UNEP", M));
    }

    /**
     * Account: a distributor holding both a debtor account and a creditor account (across its own and
     * its production code's accounts) is flagged; a distributor with only a debit, one with only a
     * credit, one with a zero-balance account, a group account and a production-code account of an
     * unknown code are not.
     */
    @Test
    @TestTransaction
    void account_compensation() {
        Period p = period(M);
        only("ACCOUNT-COMPENSATION", ControlFamily.ACCOUNT, ControlSeverity.WARNING, null);
        // Distributor with a debtor distributor account and a creditor production-code account: flagged.
        Distributor both = distributor("BOTH");
        line(p, LineNature.LINEAR, "-100").account =
                accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, both.getId());
        ProductionCode code = productionCode(both);
        line(p, LineNature.LINEAR, "100").account =
                accounts.accountFor(unep(), PayeeType.PRODUCTION_CODE, code.getId());
        // Distributor with only a debit: not flagged.
        line(p, LineNature.LINEAR, "-100").account =
                accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, distributor("ONLYDEB").getId());
        // Distributor with a zero-balance account: not flagged (neither debit nor credit).
        account(distributor("ZEROBAL"), p, "0");
        // A group account, an unknown production-code account, a production-code account whose code has
        // no distributor, and an account with no payee id are all ignored.
        accounts.accountFor(unep(), PayeeType.GROUP, 1L);
        accounts.accountFor(unep(), PayeeType.PRODUCTION_CODE, 777_777L);
        ProductionCode ownerless = new ProductionCode();
        ownerless.entity = unep();
        ownerless.code = "PCNO" + (seq++);
        ownerless.status = PartnerStatus.ACTIVE;
        ownerless.persist();
        line(p, LineNature.LINEAR, "-100").account =
                accounts.accountFor(unep(), PayeeType.PRODUCTION_CODE, ownerless.getId());
        DistributorAccount noPayee = new DistributorAccount();
        noPayee.entity = unep();
        noPayee.payeeType = PayeeType.DISTRIBUTOR;
        noPayee.payeeId = null;
        noPayee.persist();
        assertEquals(1, service.run("UNEP", M));
    }

    /**
     * Payment: a batch whose total does not match the sum of its orders is flagged; a balanced one is
     * not. A null order amount and a null batch total are treated as zero.
     */
    @Test
    @TestTransaction
    void payment_lot_balance() {
        Period p = period(M);
        only("PAY-LOT-BALANCE", ControlFamily.PAYMENT, ControlSeverity.BLOCKING, null);
        PaymentBatch off = batch(p, "100");
        paymentOrder(off, null, "40");
        paymentOrder(off, null, null);
        PaymentBatch balanced = batch(p, "50");
        paymentOrder(balanced, null, "50");
        PaymentBatch nullTotal = batch(p, null);
        paymentOrder(nullTotal, null, "0");
        assertEquals(1, service.run("UNEP", M));
    }

    /** Creates a payment batch of a period with a total. */
    private PaymentBatch batch(Period period, String total) {
        PaymentBatch b = new PaymentBatch();
        b.entity = unep();
        b.period = period;
        b.batchReference = "B" + (seq++);
        b.totalAmount = total == null ? null : new BigDecimal(total);
        b.persist();
        return b;
    }

    /** Creates a payment order in a batch, with an optional statement and an amount. */
    private PaymentOrder paymentOrder(PaymentBatch batch, DistributorAccount account, String amount) {
        PaymentOrder o = new PaymentOrder();
        o.batch = batch;
        o.account = account;
        o.amount = amount == null ? null : new BigDecimal(amount);
        o.endToEndId = "E" + (seq++);
        o.persist();
        return o;
    }

    /**
     * Payment: an order without a statement is flagged; one with a statement is not.
     */
    @Test
    @TestTransaction
    void payment_order_without_statement() {
        Period p = period(M);
        only("PAY-ORDER-NO-STMT", ControlFamily.PAYMENT, ControlSeverity.WARNING, null);
        PaymentBatch b = batch(p, "0");
        paymentOrder(b, null, "10");
        PaymentOrder withStatement = paymentOrder(b, null, "10");
        withStatement.statement = statement(p);
        assertEquals(1, service.run("UNEP", M));
    }

    /** Creates a draft statement for a period. */
    private fr.alptis.remuneration.domain.Statement statement(Period period) {
        fr.alptis.remuneration.domain.Statement s = new fr.alptis.remuneration.domain.Statement();
        s.entity = unep();
        s.period = period;
        s.statementNumber = "ST" + (seq++);
        s.versionNo = 1;
        s.persist();
        return s;
    }

    /**
     * Payment: an order whose payee is inactive is flagged; one whose payee is active, and one whose
     * account is absent, are not. An unknown and a null payee id both count as inactive.
     */
    @Test
    @TestTransaction
    void payment_inactive_payee() {
        Period p = period(M);
        only("PAY-PAYEE-INACTIVE", ControlFamily.PAYMENT, ControlSeverity.WARNING, null);
        PaymentBatch b = batch(p, "0");
        // Active payee: not flagged.
        Distributor active = distributor("PAYOK");
        membership(active, PartnerStatus.ACTIVE, LocalDate.of(2026, 1, 1), null);
        paymentOrder(b, accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, active.getId()), "10");
        // Inactive payee (an existing distributor whose membership is terminated): flagged.
        Distributor terminated = distributor("PAYKO");
        membership(terminated, PartnerStatus.TERMINATED, LocalDate.of(2026, 1, 1), null);
        paymentOrder(b, accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, terminated.getId()), "10");
        // Payee of an unknown id (no distributor row): flagged.
        paymentOrder(b, accounts.accountFor(unep(), PayeeType.DISTRIBUTOR, 888_888L), "10");
        // Order without an account: not flagged.
        paymentOrder(b, null, "10");
        // Order paid to a production-code account (not a distributor payee): not flagged.
        paymentOrder(b, accounts.accountFor(unep(), PayeeType.PRODUCTION_CODE, 5L), "10");
        assertEquals(2, service.run("UNEP", M));
    }

    /**
     * Accounting: an entry missing a side or carrying a non-positive amount is flagged; a complete one
     * is not.
     */
    @Test
    @TestTransaction
    void accounting_balanced() {
        Period p = period(M);
        only("ACC-BALANCED", ControlFamily.ACCOUNTING, ControlSeverity.BLOCKING, null);
        entry(p, null, "706", "10", null);
        entry(p, "467", "  ", "10", null);
        entry(p, "467", "706", "0", null);
        entry(p, "467", "706", null, null);
        entry(p, "467", "706", "10", null);
        assertEquals(4, service.run("UNEP", M));
    }

    /** Creates an accounting entry of a period, optionally attached to a commission line. */
    private AccountingEntry entry(Period period, String debit, String credit, String amount, CommissionLine line) {
        AccountingEntry e = new AccountingEntry();
        e.entity = unep();
        e.period = period;
        e.accountingEvent = fr.alptis.remuneration.domain.AccountingEvent.EARNING;
        e.debitAccount = debit;
        e.creditAccount = credit;
        e.amount = amount == null ? null : new BigDecimal(amount);
        e.commissionLine = line;
        e.persist();
        return e;
    }

    /**
     * Accounting: a commission line whose attached entries do not sum to its amount is flagged; one
     * whose entries match is not. A null entry amount counts as zero.
     */
    @Test
    @TestTransaction
    void accounting_entries_match_lines() {
        Period p = period(M);
        only("ACC-LINES-MATCH", ControlFamily.ACCOUNTING, ControlSeverity.BLOCKING, null);
        CommissionLine mismatch = line(p, LineNature.LINEAR, "100");
        entry(p, "467", "706", "40", mismatch);
        entry(p, "467", "706", null, mismatch);
        CommissionLine matching = line(p, LineNature.LINEAR, "50");
        entry(p, "467", "706", "50", matching);
        assertEquals(1, service.run("UNEP", M));
    }

    /**
     * External flow: an external line whose amount disagrees with its basis times its rate by more than
     * a cent is flagged; a matching one and one missing its inputs are not.
     */
    @Test
    @TestTransaction
    void external_flow_external_mismatch() {
        Period p = period(M);
        only("EXT-EXTERNAL-MISMATCH", ControlFamily.EXTERNAL_FLOW, ControlSeverity.WARNING, null);
        CommissionLine wrong = line(p, LineNature.EXTERNAL, "20.00");
        wrong.basisAmount = new BigDecimal("500");
        wrong.rate = new BigDecimal("0.035");
        CommissionLine right = line(p, LineNature.EXTERNAL, "17.50");
        right.basisAmount = new BigDecimal("500");
        right.rate = new BigDecimal("0.035");
        // Missing a basis, a rate or an amount: skipped.
        line(p, LineNature.EXTERNAL, "10.00");
        CommissionLine noRate = line(p, LineNature.EXTERNAL, "10.00");
        noRate.basisAmount = new BigDecimal("500");
        CommissionLine noAmount = line(p, LineNature.EXTERNAL, "0");
        noAmount.basisAmount = new BigDecimal("500");
        noAmount.rate = new BigDecimal("0.035");
        noAmount.amount = null;
        assertEquals(1, service.run("UNEP", M));
    }

    /**
     * External flow: an insurer commission differing from the expected is re-surfaced from the engine's
     * CAL-13 result; an equal one is not, and a blank value counts as zero.
     */
    @Test
    @TestTransaction
    void external_flow_insurer_mismatch() {
        Period p = period(M);
        only("EXT-INSURER-MISMATCH", ControlFamily.EXTERNAL_FLOW, ControlSeverity.WARNING, null);
        ControlDefinition cal13 = new ControlDefinition();
        cal13.entity = unep();
        cal13.code = "CAL-13";
        cal13.label = "expected";
        cal13.family = ControlFamily.EXTERNAL_FLOW;
        cal13.severity = ControlSeverity.WARNING;
        cal13.active = Boolean.FALSE;
        cal13.persist();
        cal13Result(p, cal13, 1L, "100", "90");
        cal13Result(p, cal13, 2L, "100", "100");
        cal13Result(p, cal13, 3L, "", "0");
        assertEquals(1, service.run("UNEP", M));
    }

    /** Records a CAL-13 expected/received result for a coverage. */
    private void cal13Result(Period period, ControlDefinition control, Long coverageId, String received,
                             String expected) {
        ControlResult r = new ControlResult();
        r.period = period;
        r.control = control;
        r.objectType = "COVERAGE";
        r.objectId = coverageId;
        r.severity = ControlSeverity.WARNING;
        r.observedValue = received;
        r.expectedValue = expected;
        r.decision = fr.alptis.remuneration.domain.ControlDecision.OPEN;
        r.persist();
    }

    /**
     * An empty flows array, a non-array flows value and a JSON-null numeric parameter all fall back to
     * the defaults rather than failing the control.
     */
    @Test
    @TestTransaction
    void empty_parameters_fall_back_to_defaults() {
        period(LocalDate.of(2026, 4, 1));
        only("COMP-FLOWS", ControlFamily.COMPLETENESS, ControlSeverity.BLOCKING, "{\"flows\":[]}");
        assertEquals(2, service.run("UNEP", LocalDate.of(2026, 4, 1)));

        // A non-array flows value falls back to the default expected flows (both missing).
        period(LocalDate.of(2026, 5, 1));
        only("COMP-FLOWS", ControlFamily.COMPLETENESS, ControlSeverity.BLOCKING, "{\"flows\":\"coverages\"}");
        assertEquals(2, service.run("UNEP", LocalDate.of(2026, 5, 1)));

        // A JSON-null numeric parameter falls back to the default deviation (0.50), so a 1 -> 3 swing is
        // still flagged.
        Period prev = period(LocalDate.of(2026, 6, 1));
        Period cur = period(LocalDate.of(2026, 7, 1));
        only("COMP-VOLUME", ControlFamily.COMPLETENESS, ControlSeverity.WARNING, "{\"deviation\":null}");
        event(prev, "SRC", M, "100", ProcessingStatus.PROCESSED, null, null);
        event(cur, "SRC", M, "100", ProcessingStatus.PROCESSED, null, null);
        event(cur, "SRC", M, "100", ProcessingStatus.PROCESSED, null, null);
        event(cur, "SRC", M, "100", ProcessingStatus.PROCESSED, null, null);
        assertEquals(1, service.run("UNEP", LocalDate.of(2026, 7, 1)));
    }

    /**
     * The parameter parser treats null, blank and malformed payloads as an empty object, and reads a
     * valid one, so a control never fails on bad configuration (the JSON column blocks malformed input
     * at persist, so this guard is exercised directly).
     */
    @Test
    void parameter_parser_tolerates_bad_payloads() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(0, ControlService.readParams(mapper, null).size());
        assertEquals(0, ControlService.readParams(mapper, "   ").size());
        assertEquals(0, ControlService.readParams(mapper, "{not json").size());
        assertEquals(1, ControlService.readParams(mapper, "{\"deviation\":0.5}").size());
    }
}
