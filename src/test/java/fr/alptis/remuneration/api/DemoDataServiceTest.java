package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.AdvanceAgreement;
import fr.alptis.remuneration.domain.AdvanceAgreementStatus;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.DistributorType;
import fr.alptis.remuneration.domain.EntityParameter;
import fr.alptis.remuneration.domain.ExternalCode;
import fr.alptis.remuneration.domain.ImportMapping;
import fr.alptis.remuneration.domain.OriasStatus;
import fr.alptis.remuneration.domain.PartnerStatus;
import fr.alptis.remuneration.domain.PayeeRuleShare;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.QualityIndicator;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
