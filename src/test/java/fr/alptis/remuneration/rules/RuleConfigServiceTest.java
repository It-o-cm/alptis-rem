package fr.alptis.remuneration.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.RuleBasis;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.domain.VersionAnchor;
import fr.alptis.remuneration.engine.FamilyRegistry;
import fr.alptis.remuneration.rules.RuleConfigService.VersionInput;
import fr.alptis.remuneration.rules.RuleConfigService.VersionView;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.DevIdentityProvider;
import fr.alptis.remuneration.security.EntityScope;
import fr.alptis.remuneration.security.SeparationOfDuties;
import fr.alptis.remuneration.security.SeparationOfDutiesException;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RuleConfigService} with hand-made scopes: plan/rule/version creation and schema
 * validation, the approval cycle with separation of duties and conflict refusal (PAR-04, PAR-05),
 * the annual duplication (PAR-12), the plausibility alerts (PAR-14) and the scope guards. Runs
 * against UNEP so the Alptis initial barème does not interfere, inside rolled-back transactions.
 */
@QuarkusTest
class RuleConfigServiceTest {

    @Inject
    AuditService audit;
    @Inject
    DateTimeProvider clock;
    @Inject
    FamilyRegistry registry;
    @Inject
    ConflictDetector conflictDetector;
    @Inject
    SeparationOfDuties separationOfDuties;
    @Inject
    ObjectMapper mapper;

    /** Builds a service seeing the given entities, acting as the given user. */
    private RuleConfigService service(List<String> entities, String user) {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(user))
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, entities)
                .build();
        CurrentUser currentUser = new CurrentUser(identity);
        return new RuleConfigService(new EntityScope(currentUser), currentUser, audit, clock, registry,
                conflictDetector, separationOfDuties, mapper);
    }

    /** A service seeing UNEP as the first administrator. */
    private RuleConfigService unep() {
        return service(List.of("UNEP"), "admin1-unep");
    }

    /** Builds a LINEAR version input with the given perimeter, dates, group and priority. */
    private static VersionInput linearInput(String scope, LocalDate from, LocalDate to, String group, int priority) {
        return new VersionInput(from, to, VersionAnchor.EVENT_DATE, scope, List.of("PREMIUM_COLLECTED"),
                RuleBasis.COLLECTED_PREMIUM, LineNature.LINEAR, "{\"allowedRates\":[0.15]}", null, null, priority, group,
                null);
    }

    /**
     * Creates a plan, a rule and a draft version, validating its parameters against the family schema.
     */
    @Test
    @TestTransaction
    void creates_plan_rule_and_version() {
        RuleConfigService svc = unep();
        svc.createPlan("UNEP", "PLAN_A", "Plan A", Direction.INBOUND, (short) 2026);
        svc.createRule("UNEP", "PLAN_A", "R1", "Règle 1", RuleFamily.LINEAR);
        VersionView view = svc.addVersion("UNEP", "PLAN_A", "R1",
                linearInput("{\"product\":[\"X\"]}", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "BASE", 10));
        assertEquals(1, view.versionNo());
        assertEquals("DRAFT", view.status());
        assertEquals(1, svc.listPlans().stream().filter(p -> p.code().equals("PLAN_A")).count());
        assertEquals(1, svc.listRules("UNEP", "PLAN_A").size());
        assertEquals(1, svc.listVersions("UNEP", "PLAN_A", "R1").size());
    }

    /**
     * A version carries its reprise policy and payment schedule (PAR-08) and its payment conditions
     * (PAR-07), all surfaced on the version view.
     */
    @Test
    @TestTransaction
    void creates_a_version_with_reprise_and_conditions() {
        RuleConfigService svc = unep();
        svc.createPlan("UNEP", "PLAN_C", "Plan C", Direction.INBOUND, (short) 2026);
        svc.createRule("UNEP", "PLAN_C", "RC", "Règle C", RuleFamily.LINEAR);
        var conditions = List.of(
                new RuleConfigService.ConditionInput("UNPAID_RATE", null, "LT", new java.math.BigDecimal("0.05"),
                        "MONTH", "DEFER"),
                new RuleConfigService.ConditionInput(null, "NEW_BUSINESS", "GE", new java.math.BigDecimal("10"),
                        "YEAR", "FORFEIT"),
                // A sparse condition with no operator nor failure mode (covers the null legs).
                new RuleConfigService.ConditionInput(null, "RAW", null, new java.math.BigDecimal("1"), null, null));
        VersionView view = svc.addVersion("UNEP", "PLAN_C", "RC", new VersionInput(
                LocalDate.of(2026, 1, 1), null, VersionAnchor.EVENT_DATE, "{\"product\":[\"X\"]}",
                List.of("PREMIUM_COLLECTED"), RuleBasis.COLLECTED_PREMIUM, LineNature.LINEAR,
                "{\"allowedRates\":[0.15]}", "{\"mode\":\"PRORATA\"}", "{\"months\":12}", 10, "BASE", conditions));
        assertEquals("{\"mode\":\"PRORATA\"}", view.clawbackPolicy());
        assertEquals("{\"months\":12}", view.paymentSchedule());
        assertEquals(3, view.conditions().size());
        var listed = svc.listVersions("UNEP", "PLAN_C", "RC").get(0);
        assertEquals(3, listed.conditions().size());
        assertTrue(listed.conditions().stream().anyMatch(c -> "UNPAID_RATE".equals(c.indicatorType())
                && "LT".equals(c.operator()) && "DEFER".equals(c.onFailure())));
        assertTrue(listed.conditions().stream().anyMatch(c -> "NEW_BUSINESS".equals(c.counterCode())
                && "FORFEIT".equals(c.onFailure())));
    }

    /**
     * Invalid family parameters are refused on version creation.
     */
    @Test
    @TestTransaction
    void refuses_invalid_parameters() {
        RuleConfigService svc = unep();
        svc.createPlan("UNEP", "PLAN_A", "Plan A", Direction.INBOUND, (short) 2026);
        svc.createRule("UNEP", "PLAN_A", "R1", "Règle 1", RuleFamily.LINEAR);
        VersionInput bad = new VersionInput(null, null, VersionAnchor.EVENT_DATE, "{}", List.of(),
                RuleBasis.COLLECTED_PREMIUM, LineNature.LINEAR, "{\"allowedRates\":[5]}", null, null, 10, "BASE", null);
        assertThrows(ValidationException.class, () -> svc.addVersion("UNEP", "PLAN_A", "R1", bad));
    }

    /**
     * A second administrator approves a draft; a conflicting version is then refused.
     */
    @Test
    @TestTransaction
    void approves_then_refuses_a_conflict() {
        RuleConfigService creator = unep();
        creator.createPlan("UNEP", "PLAN_A", "Plan A", Direction.INBOUND, (short) 2026);
        creator.createRule("UNEP", "PLAN_A", "R1", "Règle 1", RuleFamily.LINEAR);
        creator.createRule("UNEP", "PLAN_A", "R2", "Règle 2", RuleFamily.LINEAR);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        creator.addVersion("UNEP", "PLAN_A", "R1", linearInput("{\"product\":[\"X\"]}", from, to, "BASE", 10));
        creator.addVersion("UNEP", "PLAN_A", "R2", linearInput("{\"product\":[\"X\"]}", from, to, "BASE", 10));

        RuleConfigService approver = service(List.of("UNEP"), "admin2-unep");
        VersionView approved = approver.approveVersion("UNEP", "PLAN_A", "R1", 1);
        assertEquals("APPROVED", approved.status());
        assertEquals("admin2-unep", approved.approvedBy());

        assertThrows(ConflictException.class, () -> approver.approveVersion("UNEP", "PLAN_A", "R2", 1));
        // The conflict is reported on the versions list.
        assertTrue(creator.listVersions("UNEP", "PLAN_A", "R2").get(0).conflicts().size() >= 1);
    }

    /**
     * A non-conflicting version (distinct perimeter) is approved.
     */
    @Test
    @TestTransaction
    void approves_a_non_conflicting_version() {
        RuleConfigService creator = unep();
        creator.createPlan("UNEP", "PLAN_A", "Plan A", Direction.INBOUND, (short) 2026);
        creator.createRule("UNEP", "PLAN_A", "R1", "Règle 1", RuleFamily.LINEAR);
        creator.createRule("UNEP", "PLAN_A", "R2", "Règle 2", RuleFamily.LINEAR);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        creator.addVersion("UNEP", "PLAN_A", "R1", linearInput("{\"product\":[\"X\"]}", from, to, "BASE", 10));
        creator.addVersion("UNEP", "PLAN_A", "R2", linearInput("{\"product\":[\"Y\"]}", from, to, "BASE", 10));
        RuleConfigService approver = service(List.of("UNEP"), "admin2-unep");
        approver.approveVersion("UNEP", "PLAN_A", "R1", 1);
        assertEquals("APPROVED", approver.approveVersion("UNEP", "PLAN_A", "R2", 1).status());
    }

    /**
     * Approval by the creator, or of an already-approved version, is refused.
     */
    @Test
    @TestTransaction
    void refuses_self_approval_and_non_draft() {
        RuleConfigService creator = unep();
        creator.createPlan("UNEP", "PLAN_A", "Plan A", Direction.INBOUND, (short) 2026);
        creator.createRule("UNEP", "PLAN_A", "R1", "Règle 1", RuleFamily.LINEAR);
        creator.addVersion("UNEP", "PLAN_A", "R1",
                linearInput("{\"product\":[\"X\"]}", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "BASE", 10));
        assertThrows(SeparationOfDutiesException.class, () -> creator.approveVersion("UNEP", "PLAN_A", "R1", 1));

        RuleConfigService approver = service(List.of("UNEP"), "admin2-unep");
        approver.approveVersion("UNEP", "PLAN_A", "R1", 1);
        assertThrows(ValidationException.class, () -> approver.approveVersion("UNEP", "PLAN_A", "R1", 1));
    }

    /**
     * Duplicating a plan copies its rules and versions as drafts shifted to the new year.
     */
    @Test
    @TestTransaction
    void duplicates_a_plan_for_the_next_year() {
        RuleConfigService svc = unep();
        svc.createPlan("UNEP", "PLAN_A", "Plan A", Direction.INBOUND, (short) 2026);
        svc.createRule("UNEP", "PLAN_A", "R1", "Règle 1", RuleFamily.LINEAR);
        svc.addVersion("UNEP", "PLAN_A", "R1",
                linearInput("{\"product\":[\"X\"]}", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "BASE", 10));
        // A second rule whose version has no triggering types and an open end, to exercise the copy
        // of null collections and dates.
        svc.createRule("UNEP", "PLAN_A", "R2", "Règle 2", RuleFamily.LINEAR);
        svc.addVersion("UNEP", "PLAN_A", "R2", new VersionInput(LocalDate.of(2026, 1, 1), null, VersionAnchor.EVENT_DATE,
                "{\"product\":[\"Y\"]}", null, RuleBasis.COLLECTED_PREMIUM, LineNature.LINEAR,
                "{\"allowedRates\":[0.1]}", null, null, 10, "BASE", null));

        svc.duplicatePlan("UNEP", "PLAN_A", "PLAN_A_2027", (short) 2027);
        List<VersionView> copied = svc.listVersions("UNEP", "PLAN_A_2027", "R1");
        assertEquals(1, copied.size());
        assertEquals("DRAFT", copied.get(0).status());
        assertEquals("2027-01-01", copied.get(0).validFrom());
        assertEquals("2027-01-01", svc.listVersions("UNEP", "PLAN_A_2027", "R2").get(0).validFrom());
        assertThrows(ConflictException.class, () -> svc.duplicatePlan("UNEP", "PLAN_A", "PLAN_A_2027", (short) 2027));
    }

    /**
     * Plausibility alerts flag an unlikely rate and ignore absent, blank or malformed payloads.
     */
    @Test
    void raises_plausibility_alerts() {
        RuleConfigService svc = service(List.of("UNEP"), "admin1-unep");
        // An unlikely acquisition and an unlikely servicing rate are both flagged.
        assertEquals(3, svc.plausibilityAlerts(
                "{\"acquisition\":0.9,\"servicing\":0.8,\"annualRate\":0.7,\"firstYearDays\":365,\"counterCode\":\"X\"}")
                .size());
        // A plausible linear rate (a rate-like key) raises nothing.
        assertTrue(svc.plausibilityAlerts("{\"allowedRates\":[0.15]}").isEmpty());
        assertTrue(svc.plausibilityAlerts(null).isEmpty());
        assertTrue(svc.plausibilityAlerts("   ").isEmpty());
        assertTrue(svc.plausibilityAlerts("{not json").isEmpty());
    }

    /**
     * Views tolerate optional null fields: a plan with no direction, a rule with no family and a
     * version with no dates, status or priority.
     */
    @Test
    @TestTransaction
    void views_tolerate_null_optional_fields() {
        fr.alptis.remuneration.domain.LegalEntity unep =
                fr.alptis.remuneration.domain.LegalEntity.find("code", "UNEP").firstResult();
        fr.alptis.remuneration.domain.CommissionPlan plan = new fr.alptis.remuneration.domain.CommissionPlan();
        plan.entity = unep;
        plan.code = "BARE";
        plan.label = "Nu";
        plan.vintage = (short) 2026;
        plan.persist();
        fr.alptis.remuneration.domain.Rule rule = new fr.alptis.remuneration.domain.Rule();
        rule.plan = plan;
        rule.code = "BR";
        rule.label = "Règle nue";
        rule.persist();
        fr.alptis.remuneration.domain.RuleVersion v = new fr.alptis.remuneration.domain.RuleVersion();
        v.rule = rule;
        v.versionNo = 1;
        v.persist();

        RuleConfigService svc = unep();
        assertTrue(svc.listPlans().stream().anyMatch(p -> p.code().equals("BARE") && p.direction() == null));
        assertTrue(svc.listRules("UNEP", "BARE").stream().anyMatch(r -> r.family() == null));
        VersionView view = svc.listVersions("UNEP", "BARE", "BR").get(0);
        assertEquals(null, view.validFrom());
        assertEquals(null, view.status());
    }

    /**
     * Duplicate codes and out-of-scope or unknown references are refused.
     */
    @Test
    @TestTransaction
    void refuses_duplicates_and_unknown_references() {
        RuleConfigService svc = unep();
        svc.createPlan("UNEP", "PLAN_A", "Plan A", Direction.INBOUND, (short) 2026);
        assertThrows(ConflictException.class,
                () -> svc.createPlan("UNEP", "PLAN_A", "Plan A", Direction.INBOUND, (short) 2026));
        svc.createRule("UNEP", "PLAN_A", "R1", "Règle 1", RuleFamily.LINEAR);
        assertThrows(ConflictException.class, () -> svc.createRule("UNEP", "PLAN_A", "R1", "x", RuleFamily.LINEAR));

        assertThrows(ResourceNotFoundException.class,
                () -> svc.createPlan("ALPTIS_ASSURANCES", "P", "p", Direction.INBOUND, (short) 2026));
        assertThrows(ResourceNotFoundException.class,
                () -> svc.createPlan("NOPE", "P", "p", Direction.INBOUND, (short) 2026));
        assertThrows(ResourceNotFoundException.class, () -> svc.listRules("UNEP", "NOPE"));
        assertThrows(ResourceNotFoundException.class, () -> svc.listVersions("UNEP", "PLAN_A", "NOPE"));
        assertThrows(ResourceNotFoundException.class, () -> svc.approveVersion("UNEP", "PLAN_A", "R1", 9));
    }

    /**
     * An unrestricted user lists plans across entities (including the Alptis barème).
     */
    @Test
    void unrestricted_lists_every_plan() {
        RuleConfigService all = service(List.of("*"), "auditor");
        assertTrue(all.listPlans().stream().anyMatch(p -> p.code().equals("GUIDE_2026")));
    }
}
