package fr.alptis.remuneration.rules;

import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ConflictDetector}: the group, priority, date-overlap and perimeter-overlap branches
 * of section 6.11, including scalar criteria, non-object and malformed perimeters and empty value
 * sets. Each scenario seeds its own candidate and other version in a rolled-back transaction.
 */
@QuarkusTest
class ConflictDetectorTest {

    @Inject
    ConflictDetector detector;

    /** Returns a rule in UNEP, creating the plan and rule on first use. */
    private Rule rule() {
        LegalEntity unep = LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep;
        plan.code = "CONFLICT_PLAN";
        plan.label = "Conflict";
        plan.direction = Direction.INBOUND;
        plan.vintage = (short) 2026;
        plan.persist();
        Rule rule = new Rule();
        rule.plan = plan;
        rule.code = "R";
        rule.label = "R";
        rule.family = RuleFamily.LINEAR;
        rule.persist();
        return rule;
    }

    /** Persists a version of the rule with the given attributes. */
    private RuleVersion version(Rule rule, int no, String scope, LocalDate from, LocalDate to, String group,
                                int priority, VersionStatus status) {
        RuleVersion v = new RuleVersion();
        v.rule = rule;
        v.versionNo = no;
        v.scope = scope;
        v.validFrom = from;
        v.validTo = to;
        v.exclusionGroup = group;
        v.priority = priority;
        v.status = status;
        v.persist();
        return v;
    }

    private static final LocalDate Y26_FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate Y26_TO = LocalDate.of(2026, 12, 31);

    /**
     * Overlapping perimeter, same group, priority and period yields a conflict; the candidate itself
     * is never counted.
     */
    @Test
    @TestTransaction
    void detects_an_overlapping_conflict() {
        Rule rule = rule();
        version(rule, 1, "{\"product\":[\"X\"]}", Y26_FROM, Y26_TO, "BASE", 10, VersionStatus.ACTIVE);
        RuleVersion candidate = version(rule, 2, "{\"product\":[\"X\"]}", Y26_FROM, Y26_TO, "BASE", 10,
                VersionStatus.ACTIVE);
        assertEquals(1, detector.conflicts(candidate).size());
    }

    /**
     * Different group or different priority never conflicts.
     */
    @Test
    @TestTransaction
    void different_group_or_priority_does_not_conflict() {
        Rule rule = rule();
        version(rule, 1, "{\"product\":[\"X\"]}", Y26_FROM, Y26_TO, "BONUS", 10, VersionStatus.ACTIVE);
        version(rule, 2, "{\"product\":[\"X\"]}", Y26_FROM, Y26_TO, "BASE", 20, VersionStatus.ACTIVE);
        RuleVersion candidate = version(rule, 3, "{\"product\":[\"X\"]}", Y26_FROM, Y26_TO, "BASE", 10,
                VersionStatus.ACTIVE);
        assertTrue(detector.conflicts(candidate).isEmpty());
    }

    /**
     * Non-overlapping periods, in either direction, never conflict.
     */
    @Test
    @TestTransaction
    void non_overlapping_periods_do_not_conflict() {
        Rule rule = rule();
        version(rule, 1, "{\"product\":[\"X\"]}", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "BASE", 10,
                VersionStatus.ACTIVE);
        version(rule, 2, "{\"product\":[\"X\"]}", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), "BASE", 10,
                VersionStatus.ACTIVE);
        RuleVersion candidate = version(rule, 3, "{\"product\":[\"X\"]}", Y26_FROM, Y26_TO, "BASE", 10,
                VersionStatus.ACTIVE);
        assertTrue(detector.conflicts(candidate).isEmpty());
    }

    /**
     * Disjoint perimeters (scalar and array, and an empty value set) do not conflict, while a shared
     * scalar value does.
     */
    @Test
    @TestTransaction
    void perimeter_overlap_rules() {
        Rule rule = rule();
        // Disjoint scalar entity value.
        version(rule, 1, "{\"entity\":\"ALPTIS\"}", Y26_FROM, Y26_TO, "BASE", 10, VersionStatus.ACTIVE);
        // Empty value set is treated as "all" and overlaps.
        version(rule, 2, "{\"product\":[]}", Y26_FROM, Y26_TO, "BASE", 10, VersionStatus.ACTIVE);
        RuleVersion candidate = version(rule, 3, "{\"entity\":\"UNEP\",\"product\":[\"X\"]}", Y26_FROM, Y26_TO,
                "BASE", 10, VersionStatus.ACTIVE);
        // Conflicts with version 2 (empty set overlaps) but not version 1 (entity disjoint).
        assertEquals(1, detector.conflicts(candidate).size());
    }

    /**
     * Open-ended periods (null bounds) overlap any period.
     */
    @Test
    @TestTransaction
    void open_ended_periods_overlap() {
        Rule rule = rule();
        version(rule, 1, "{\"product\":[\"X\"]}", null, null, "BASE", 10, VersionStatus.ACTIVE);
        RuleVersion candidate = version(rule, 2, "{\"product\":[\"X\"]}", null, null, "BASE", 10,
                VersionStatus.ACTIVE);
        assertEquals(1, detector.conflicts(candidate).size());
    }

    /**
     * A candidate with a closed period conflicts with an other that has an open end (null validTo).
     */
    @Test
    @TestTransaction
    void open_end_on_the_other_overlaps() {
        Rule rule = rule();
        version(rule, 1, "{\"product\":[\"X\"]}", LocalDate.of(2025, 1, 1), null, "BASE", 10, VersionStatus.ACTIVE);
        RuleVersion candidate = version(rule, 2, "{\"product\":[\"X\"]}", Y26_FROM, Y26_TO, "BASE", 10,
                VersionStatus.ACTIVE);
        assertEquals(1, detector.conflicts(candidate).size());
    }

    /**
     * A candidate with an open end conflicts with a later closed-period other.
     */
    @Test
    @TestTransaction
    void open_end_on_the_candidate_overlaps() {
        Rule rule = rule();
        version(rule, 1, "{\"product\":[\"X\"]}", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), "BASE", 10,
                VersionStatus.ACTIVE);
        RuleVersion candidate = version(rule, 2, "{\"product\":[\"X\"]}", Y26_FROM, null, "BASE", 10,
                VersionStatus.ACTIVE);
        assertEquals(1, detector.conflicts(candidate).size());
    }

    /**
     * A null perimeter stored in the database is treated as "all" and overlaps.
     */
    @Test
    @TestTransaction
    void null_perimeter_overlaps() {
        Rule rule = rule();
        version(rule, 1, null, Y26_FROM, Y26_TO, "BASE", 10, VersionStatus.ACTIVE);
        RuleVersion candidate = version(rule, 2, "{\"product\":[\"X\"]}", Y26_FROM, Y26_TO, "BASE", 10,
                VersionStatus.ACTIVE);
        assertEquals(1, detector.conflicts(candidate).size());
    }
}
