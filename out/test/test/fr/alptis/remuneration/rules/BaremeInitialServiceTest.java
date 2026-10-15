package fr.alptis.remuneration.rules;

import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Rule;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link BaremeInitialService}: the 2026 Guide base barème (section 6.12) is seeded for Alptis
 * at startup with the exact rates of the specification, and seeding is idempotent.
 */
@QuarkusTest
class BaremeInitialServiceTest {

    @Inject
    BaremeInitialService service;

    /** Returns an entity by code. */
    private static LegalEntity entity(String code) {
        return LegalEntity.<LegalEntity>find("code", code).firstResult();
    }

    /**
     * The barème seeded at startup carries the plan, the base rules and the exact rates of section 6.12.
     */
    @Test
    void seeds_the_guide_2026_barème_for_alptis() {
        LegalEntity alptis = entity("ALPTIS_ASSURANCES");
        CommissionPlan plan = CommissionPlan.find("entity = ?1 and code = ?2", alptis, "GUIDE_2026").firstResult();
        assertNotNull(plan);
        assertEquals((short) 2026, plan.vintage);

        RuleVersion selectUpfront = version(plan, "SANTE_SELECT_UPFRONT");
        assertEquals(VersionStatus.ACTIVE, selectUpfront.status);
        assertTrue(selectUpfront.parameters.contains("\"acquisition\":0.3"));
        assertTrue(selectUpfront.parameters.contains("\"servicing\":0.1"));
        assertTrue(selectUpfront.parameters.contains("\"firstYearDays\":365"));
        assertTrue(selectUpfront.clawbackPolicy.contains("\"rate\":1.0"));

        RuleVersion proLinear = version(plan, "SANTE_PRO_PLUS_LINEAR");
        assertTrue(proLinear.parameters.contains("0.05"));
        assertTrue(proLinear.parameters.contains("0.2"));

        RuleVersion dependance = version(plan, "GARANTIE_DEPENDANCE_UPFRONT");
        assertTrue(dependance.parameters.contains("\"acquisition\":0.55"));
        assertTrue(dependance.parameters.contains("\"servicing\":0.05"));
    }

    /**
     * Seeding again changes nothing (idempotent); seeding a fresh entity creates the whole barème.
     */
    @Test
    @TestTransaction
    void seeding_is_idempotent_and_counts_its_rules() {
        assertEquals(0, service.seed(entity("ALPTIS_ASSURANCES")));
        assertEquals(0, service.seed(null));
        assertEquals(31, service.seed(entity("UNEP")));
    }

    /**
     * Returns the first (and only) version of a base rule of the plan.
     *
     * @param plan     the plan
     * @param ruleCode the rule code
     * @return the rule version
     */
    private static RuleVersion version(CommissionPlan plan, String ruleCode) {
        Rule rule = Rule.find("plan = ?1 and code = ?2", plan, ruleCode).firstResult();
        assertNotNull(rule, ruleCode);
        return RuleVersion.find("rule = ?1 and versionNo = 1", rule).firstResult();
    }
}
