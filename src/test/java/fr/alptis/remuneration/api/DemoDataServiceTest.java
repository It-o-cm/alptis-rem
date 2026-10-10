package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.Adjustment;
import fr.alptis.remuneration.domain.AdjustmentStatus;
import fr.alptis.remuneration.domain.AuditEvent;
import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementStatus;
import fr.alptis.remuneration.domain.AssignmentRole;
import fr.alptis.remuneration.domain.BatchStatus;
import fr.alptis.remuneration.domain.BatchType;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ControlDecision;
import fr.alptis.remuneration.domain.ControlResult;
import fr.alptis.remuneration.domain.ControlSeverity;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CoverageAssignment;
import fr.alptis.remuneration.domain.CutoffExtract;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.DistributorType;
import fr.alptis.remuneration.domain.Employee;
import fr.alptis.remuneration.domain.EmployeeMeasure;
import fr.alptis.remuneration.domain.EntityParameter;
import fr.alptis.remuneration.domain.ExportBatch;
import fr.alptis.remuneration.domain.ExternalCode;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ImportBatchStatus;
import fr.alptis.remuneration.domain.ImportMapping;
import fr.alptis.remuneration.domain.ImportRejection;
import fr.alptis.remuneration.domain.ImportRejectionStatus;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.PayeeRuleShare;
import fr.alptis.remuneration.domain.PaymentBatch;
import fr.alptis.remuneration.domain.PaymentOrder;
import fr.alptis.remuneration.domain.PaymentOrderStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.PortfolioTransfer;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.RemunerationChoice;
import fr.alptis.remuneration.domain.ReplacementOrder;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.domain.TransferStatus;
import fr.alptis.remuneration.domain.VariablePlan;
import fr.alptis.remuneration.domain.VariableResult;
import fr.alptis.remuneration.domain.VariableResultStatus;
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance of the demonstration dataset (brief {@code DONNEES-DEMO.md}). Each test asserts, floor by
 * floor, the minimal content of section 4; this class grows with the dataset block by block. The
 * referential floors are checked here, together with a second load being idempotent.
 */
@QuarkusTest
class DemoDataServiceTest {

    @Inject
    DemoDataService service;
    @Inject
    TestDatabaseReset reset;

    /** Resets the database to its seeded baseline before each test. */
    @BeforeEach
    void setUp() {
        reset.reset();
    }

    /**
     * The referential floors of section 4 are all met: twenty-five-plus distributors covering every
     * type, ORIAS status and partner status, two groups, products on several ranges, an active rule
     * for each of the nine families, a multi-version rule with a four-eyes-pending draft, three
     * discount conventions in different states, and the supporting referential screens' data.
     */
    @Test
    void builds_the_referential_floors() {
        service.load();

        // At least 25 distributors, mock-up cabinet included.
        assertEquals(1, Distributor.count("partnerCode", DemoCodes.CABINET));
        assertTrue(Distributor.count() >= 26, "at least 25 distributors besides the cabinet");

        // Every distributor type and both ORIAS statuses are represented.
        Set<DistributorType> types = EnumSet.noneOf(DistributorType.class);
        Set<OriasStatus> orias = EnumSet.noneOf(OriasStatus.class);
        for (Distributor d : Distributor.<Distributor>listAll()) {
            types.add(d.distributorType);
            orias.add(d.oriasStatus);
        }
        assertEquals(EnumSet.allOf(DistributorType.class), types);
        assertEquals(EnumSet.allOf(OriasStatus.class), orias);

        // Every partner status is represented across the distributor-entity links.
        Set<PartnerStatus> partner = EnumSet.noneOf(PartnerStatus.class);
        for (DistributorEntity link : DistributorEntity.<DistributorEntity>listAll()) {
            partner.add(link.status);
        }
        assertEquals(EnumSet.allOf(PartnerStatus.class), partner);

        // At least two groups, and at least three distributors working with both entities.
        assertTrue(DistributorGroup.count() >= 2);
        long bothEntities = 0;
        for (Distributor d : Distributor.<Distributor>listAll()) {
            if (DistributorEntity.count("distributor = ?1", d) >= 2) {
                bothEntities++;
            }
        }
        assertTrue(bothEntities >= 3, "at least three distributors with both entities");

        // At least eight products on several ranges, for Alptis.
        assertTrue(Product.count("entity.code", DemoCodes.ALPTIS) >= 8);

        // Each of the nine families has at least one active rule version.
        for (RuleFamily family : RuleFamily.values()) {
            assertTrue(RuleVersion.count("rule.family = ?1 and status = ?2", family, VersionStatus.ACTIVE) > 0,
                    "an active rule for family " + family);
        }

        // A rule carries retired, active and draft versions, the draft awaiting four-eyes approval.
        java.util.Map<Long, Integer> versionsPerRule = new java.util.HashMap<>();
        for (RuleVersion v : RuleVersion.<RuleVersion>listAll()) {
            versionsPerRule.merge(v.rule.getId(), 1, Integer::sum);
        }
        assertTrue(versionsPerRule.values().stream().anyMatch(c -> c >= 3), "a rule with at least three versions");
        assertTrue(RuleVersion.count("status = ?1 and approvedBy is null", VersionStatus.DRAFT) > 0,
                "a draft version awaiting four-eyes approval");

        // Three discount conventions in three different states.
        for (AdvanceAgreementStatus status : AdvanceAgreementStatus.values()) {
            assertTrue(AdvanceAgreement.count("status", status) > 0, "a discount convention " + status);
        }

        // The supporting referential screens all have data.
        assertTrue(ImportMapping.count() >= 2);
        assertTrue(ExternalCode.count() >= 2);
        assertTrue(PayeeRuleShare.count() >= 2);
        assertTrue(QualityIndicator.count() >= 2);
        assertTrue(EntityParameter.count() >= 2);
    }

    /**
     * The contracts floors of section 4 are met: at least 150 contracts and 200 coverages, co-broked
     * coverages, a discount choice, two portfolio transfers (one processed, one in progress) and a
     * replacement order.
     */
    @Test
    void builds_the_contracts_floors() {
        service.load();

        assertTrue(Contract.count() >= 150, "at least 150 contracts");
        assertTrue(Coverage.count() >= 200, "at least 200 coverages");
        assertTrue(CoverageAssignment.count("role", AssignmentRole.CO_BROKER) > 0, "a co-broked coverage");
        assertTrue(RemunerationChoice.count("advanceRequested", Boolean.TRUE) > 0, "a discount choice");
        assertTrue(PortfolioTransfer.count("status", TransferStatus.PROCESSED) > 0, "a processed transfer");
        assertTrue(PortfolioTransfer.count("status", TransferStatus.PENDING) > 0, "an in-progress transfer");
        assertTrue(ReplacementOrder.count() > 0, "a replacement order");
    }

    /**
     * The cycle floors of section 4 are met: the periods are in the required states, the mock-up
     * cabinet's September bordereau nets 110,00 €, every family produced lines, the controls span the
     * three severities with an open blocking anomaly in October, the adjustments span their statuses,
     * and the closed periods carry paid lots, an off-cycle lot, a rejected order, a rectified bordereau
     * and a cut-off extract.
     */
    @Test
    void drives_the_cycle_floors() {
        service.load();

        // Period states: Alptis June–September closed, October calculated, November open.
        assertEquals(PeriodState.CLOSED, period(DemoCodes.ALPTIS, 6).state);
        assertEquals(PeriodState.CLOSED, period(DemoCodes.ALPTIS, 9).state);
        assertEquals(PeriodState.CALCULATED, period(DemoCodes.ALPTIS, 10).state);
        assertEquals(PeriodState.OPEN, period(DemoCodes.ALPTIS, 11).state);
        // UNEP: September closed, October stopped at payment-prepared.
        assertEquals(PeriodState.CLOSED, period(DemoCodes.UNEP, 9).state);
        assertEquals(PeriodState.PAYMENT_PREPARED, period(DemoCodes.UNEP, 10).state);

        // The mock-up scene: a 110,00 € September bordereau for cabinet P13897.
        Period alptisSep = period(DemoCodes.ALPTIS, 9);
        assertTrue(Statement.count("period = ?1 and netAmount = ?2", alptisSep, new BigDecimal("110.00")) > 0,
                "a 110,00 € September bordereau");

        // Every family produced at least one line, and the reprises produced clawback and inverse lines.
        for (LineNature nature : new LineNature[]{LineNature.LINEAR, LineNature.ACQUISITION, LineNature.SERVICING,
                LineNature.ADVANCE, LineNature.RETROCESSION, LineNature.OUTSTANDING, LineNature.FEE,
                LineNature.EXTERNAL, LineNature.RCD, LineNature.RCP, LineNature.COMPLEMENT, LineNature.CLAWBACK}) {
            assertTrue(CommissionLine.count("nature", nature) > 0, "a line of nature " + nature);
        }
        // A cancellation (D12) produced an inverse (negative) line.
        assertTrue(CommissionLine.count("amount < ?1", BigDecimal.ZERO) > 0, "an inverse/clawback line");

        // Controls span the three severities, with an open blocking anomaly in October.
        for (ControlSeverity severity : ControlSeverity.values()) {
            assertTrue(ControlResult.count("severity", severity) > 0, "a control result of severity " + severity);
        }
        assertTrue(ControlResult.count("period = ?1 and severity = ?2 and decision = ?3",
                period(DemoCodes.ALPTIS, 10), ControlSeverity.BLOCKING, ControlDecision.OPEN) > 0,
                "an open blocking anomaly in October");

        // Adjustments span their statuses, including one awaiting validation and one refused.
        for (AdjustmentStatus status : new AdjustmentStatus[]{AdjustmentStatus.SUBMITTED, AdjustmentStatus.APPLIED,
                AdjustmentStatus.REJECTED, AdjustmentStatus.DRAFT}) {
            assertTrue(Adjustment.count("status", status) > 0, "an adjustment " + status);
        }

        // Paid lots, an off-cycle lot, a rejected bank order and a rectified bordereau.
        assertTrue(PaymentBatch.count("batchType = ?1 and status = ?2", BatchType.CYCLE, BatchStatus.ISSUED) > 0,
                "a validated cycle lot");
        assertTrue(PaymentBatch.count("batchType", BatchType.OFF_CYCLE) > 0, "an off-cycle lot");
        assertTrue(PaymentOrder.count("status", PaymentOrderStatus.REJECTED) > 0, "a rejected bank order");
        assertTrue(Statement.count("replacesStatement is not null") > 0, "a rectified bordereau");

        // A cut-off extract for each closed period (Alptis June–September, UNEP September).
        assertTrue(CutoffExtract.count() >= 5, "a cut-off extract per closed period");
    }

    /**
     * The sales-force floors of section 4 are met: a variable plan per post, at least twelve
     * collaborators over two supervision levels, objectives, measures, a validated-and-exported month
     * and a pending month, and the Alptis sales rep's own perimeter is not empty.
     */
    @Test
    void builds_the_sales_force_floors() {
        service.load();

        assertTrue(VariablePlan.count() >= 2, "a variable plan per post");
        assertTrue(Employee.count() >= 12, "at least twelve collaborators");
        assertTrue(Employee.count("manager is not null") > 0, "collaborators under a manager");
        assertTrue(Employee.count("manager is null") > 0, "managers at the top level");
        assertTrue(EmployeeMeasure.count() > 0, "sales-force measures");

        Period september = period(DemoCodes.ALPTIS, 9);
        assertTrue(VariableResult.count("period = ?1 and status = ?2", september,
                VariableResultStatus.EXPORTED) > 0, "a validated-and-exported month");
        Period october = period(DemoCodes.ALPTIS, 10);
        assertTrue(VariableResult.count("period = ?1 and status = ?2", october,
                VariableResultStatus.CALCULATED) > 0, "a pending month");

        // The Alptis sales rep sees their own variable (its employee carries the dev username).
        Employee rep = Employee.<Employee>find("externalId", "rep-alptis").firstResult();
        assertTrue(VariableResult.count("employee = ?1", rep) > 0, "the sales rep's own results");
    }

    /**
     * The flows and administration floors of section 4 are met: import batches including one partially
     * loaded with rejections of several reasons (some handled), export lots, and an audit trail with
     * varied authors (not a single "demo" user).
     */
    @Test
    void builds_the_flux_and_audit_floors() {
        service.load();

        assertTrue(ImportBatch.count() >= 4, "import batches");
        assertTrue(ImportBatch.count("status", ImportBatchStatus.PARTIALLY_LOADED) > 0, "a partial-error batch");
        long reasons = ImportRejection.<ImportRejection>listAll().stream()
                .map(r -> r.reasonCode).distinct().count();
        assertTrue(reasons >= 3, "rejections of several reasons");
        assertTrue(ImportRejection.count("status", ImportRejectionStatus.CORRECTED) > 0, "a corrected rejection");
        assertTrue(ImportRejection.count("status", ImportRejectionStatus.DISCARDED) > 0, "a discarded rejection");
        assertTrue(ExportBatch.count() > 0, "an export journal");

        // The audit trail reflects the demo with varied authors (not a single "demo" user).
        long authors = AuditEvent.<AuditEvent>listAll().stream().map(a -> a.userId).distinct().count();
        assertTrue(authors >= 4, "an audit trail with varied authors");
    }

    /**
     * Returns an Alptis/UNEP period of 2026 by month number.
     *
     * @param entityCode the entity code
     * @param month      the month number
     * @return the period
     */
    private static Period period(String entityCode, int month) {
        return Period.<Period>find("entity.code = ?1 and periodMonth = ?2", entityCode,
                LocalDate.of(2026, month, 1)).firstResult();
    }

    /**
     * A second load changes nothing: the mock-up cabinet already exists, so the dataset is left as is.
     */
    @Test
    void second_load_is_idempotent() {
        service.load();
        long distributors = Distributor.count();
        service.load();
        assertEquals(distributors, Distributor.count());
    }
}
