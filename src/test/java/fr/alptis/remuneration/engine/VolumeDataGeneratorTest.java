package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.engine.VolumeDataGenerator.VolumeCounts;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the volume generator: it plants the requested beneficiaries and events, reuses an existing
 * product and plan, and rejects an unknown entity. Runs against UNEP in rolled-back transactions.
 */
@QuarkusTest
class VolumeDataGeneratorTest {

    @Inject
    VolumeDataGenerator generator;

    private static final LocalDate MONTH = LocalDate.of(2026, 9, 1);

    /** Returns the UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /**
     * The generator plants the requested counts of beneficiaries and events.
     */
    @Test
    @TestTransaction
    void generates_the_requested_counts() {
        VolumeCounts counts = generator.generate("UNEP", MONTH, 2, 3);
        assertEquals(2, counts.beneficiaries());
        assertEquals(6, counts.events());
        assertEquals(6, BusinessEvent.count("sourceSystem = ?1", "VOLUME"));
        assertEquals(2, Coverage.count("coverageCode like ?1", "VOL-%"));
    }

    /**
     * When the product and the plan already exist, the generator reuses them and only adds the
     * beneficiaries.
     */
    @Test
    @TestTransaction
    void reuses_an_existing_product_and_plan() {
        Product product = new Product();
        product.entity = unep();
        product.code = VolumeDataGenerator.PRODUCT;
        product.label = "Déjà là";
        product.persist();
        CommissionPlan plan = new CommissionPlan();
        plan.entity = unep();
        plan.code = "VOLUME_PLAN";
        plan.label = "Déjà là";
        plan.direction = Direction.OUTBOUND;
        plan.vintage = (short) 2026;
        plan.persist();

        VolumeCounts counts = generator.generate("UNEP", MONTH, 1, 1);
        assertEquals(1, counts.beneficiaries());
        assertEquals(1, Product.count("entity = ?1 and code = ?2", unep(), VolumeDataGenerator.PRODUCT));
        assertEquals(1, CommissionPlan.count("entity = ?1 and code = ?2", unep(), "VOLUME_PLAN"));
    }

    /**
     * An unknown entity is rejected.
     */
    @Test
    @TestTransaction
    void rejects_an_unknown_entity() {
        assertThrows(fr.alptis.remuneration.domain.ResourceNotFoundException.class,
                () -> generator.generate("GHOST", MONTH, 1, 1));
    }
}
