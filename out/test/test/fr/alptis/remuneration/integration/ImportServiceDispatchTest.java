package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ValidationException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link ImportService}: every flow code of section 9.9 is dispatched to its pivot importer,
 * and an unknown flow code is rejected. Header-only files are enough to exercise the dispatch.
 */
@QuarkusTest
class ImportServiceDispatchTest {

    @Inject
    ImportService service;

    /** The flow codes the facade must recognise. */
    private static final String[] FLOWS = {
            "events", "coverages", "distributors", "distributor_groups", "production_codes",
            "insurers", "product_ranges", "products", "product_options", "bank_accounts",
            "quality_indicators", "portfolio_transfers", "replacement_orders", "payee_rules",
            "external_codes",
    };

    /**
     * Every known flow code is dispatched and returns a journalled batch for the right flow.
     */
    @Test
    @TestTransaction
    void dispatches_every_known_flow() {
        for (String flow : FLOWS) {
            ImportBatch batch = service.importFile("ALPTIS_ASSURANCES", flow, "SYS",
                    flow + ".csv", "header_a;header_b\n");
            assertNotNull(batch);
            assertEquals(flow, batch.flowCode);
        }
    }

    /**
     * An unknown flow code is rejected.
     */
    @Test
    @TestTransaction
    void rejects_an_unknown_flow() {
        assertThrows(ValidationException.class,
                () -> service.importFile("ALPTIS_ASSURANCES", "bogus", "SYS", "x.csv", "a;b\n"));
    }
}
