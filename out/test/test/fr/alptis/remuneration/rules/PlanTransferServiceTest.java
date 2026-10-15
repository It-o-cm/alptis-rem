package fr.alptis.remuneration.rules;

import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleBasis;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.domain.VersionAnchor;
import fr.alptis.remuneration.domain.VersionStatus;
import fr.alptis.remuneration.rules.PlanTransferService.PlanExport;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the plan export and import (PAR-15): a plan round-trips to a new code with every version
 * recreated in {@code DRAFT}; an existing target code and unknown references are rejected. Runs
 * against UNEP in rolled-back transactions.
 */
@QuarkusTest
class PlanTransferServiceTest {

    @Inject
    PlanTransferService service;

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Builds a source plan with one rule and two versions (one fully set, one with nulls). */
    private void sourcePlan() {
        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep();
        plan.code = "SRC";
        plan.label = "Source";
        plan.direction = Direction.OUTBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = "R1";
        rule.label = "Règle 1";
        rule.family = RuleFamily.LINEAR;
        rule.persist();
        RuleVersion full = new RuleVersion();
        full.rule = rule;
        full.versionNo = 1;
        full.status = VersionStatus.ACTIVE;
        full.validFrom = LocalDate.of(2026, 1, 1);
        full.validTo = LocalDate.of(2026, 12, 31);
        full.versionAnchor = VersionAnchor.EVENT_DATE;
        full.scope = "{\"entity\":[\"UNEP\"]}";
        full.basis = RuleBasis.COLLECTED_PREMIUM;
        full.outputNature = LineNature.LINEAR;
        full.parameters = "{\"allowedRates\":[0.15]}";
        full.priority = 10;
        full.exclusionGroup = "BASE";
        full.persist();
        RuleVersion sparse = new RuleVersion();
        sparse.rule = rule;
        sparse.versionNo = 2;
        sparse.status = VersionStatus.DRAFT;
        sparse.persist();
    }

    /**
     * A plan exports its rules and versions and imports back under a new code, every version in draft;
     * the round-trip preserves the fields.
     */
    @Test
    @TestTransaction
    void exports_and_imports_round_trip() {
        sourcePlan();

        PlanExport export = service.export("UNEP", "SRC");
        assertEquals("SRC", export.code());
        assertEquals(1, export.rules().size());
        assertEquals(2, export.rules().get(0).versions().size());
        assertEquals("EVENT_DATE", export.rules().get(0).versions().get(0).versionAnchor());

        String created = service.importPlan("UNEP", "DST", export);
        assertEquals("DST", created);
        CommissionPlan imported = CommissionPlan.find("entity = ?1 and code = ?2", unep(), "DST").firstResult();
        assertEquals("Source", imported.label);
        assertEquals(2, RuleVersion.count("rule.plan = ?1", imported));
        assertEquals(0, RuleVersion.count("rule.plan = ?1 and status <> ?2", imported, VersionStatus.DRAFT));

        PlanExport again = service.export("UNEP", "DST");
        assertEquals(RuleBasis.COLLECTED_PREMIUM.name(), again.rules().get(0).versions().get(0).basis());
    }

    /**
     * Importing under an existing code is refused; unknown plan and entity are not found.
     */
    @Test
    @TestTransaction
    void rejects_existing_code_and_unknown_references() {
        sourcePlan();
        PlanExport export = service.export("UNEP", "SRC");
        assertThrows(ValidationException.class, () -> service.importPlan("UNEP", "SRC", export));
        assertThrows(ResourceNotFoundException.class, () -> service.export("UNEP", "NOPE"));
        assertThrows(ResourceNotFoundException.class, () -> service.export("GHOST", "SRC"));
        assertThrows(ResourceNotFoundException.class, () -> service.importPlan("GHOST", "X", export));
    }
}
