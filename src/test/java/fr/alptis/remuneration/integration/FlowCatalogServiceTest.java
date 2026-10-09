package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.integration.FlowCatalogService.FlowDirection;
import fr.alptis.remuneration.integration.FlowCatalogService.FlowView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the flow catalogue (section 9.1, ING-10): the full inventory and the direction
 * filter. Pure, without a container.
 */
class FlowCatalogServiceTest {

    private final FlowCatalogService service = new FlowCatalogService();

    /**
     * The unfiltered catalogue lists the nine inbound and seven outbound flows, starting with IN-01.
     */
    @Test
    void lists_every_flow() {
        List<FlowView> all = service.catalog(null);
        assertEquals(16, all.size());
        assertEquals("IN-01", all.get(0).id());
        assertEquals(FlowDirection.INBOUND, all.get(0).direction());
    }

    /**
     * The direction filter keeps only inbound or only outbound flows.
     */
    @Test
    void filters_by_direction() {
        List<FlowView> inbound = service.catalog(FlowDirection.INBOUND);
        assertEquals(9, inbound.size());
        assertTrue(inbound.stream().allMatch(f -> f.direction() == FlowDirection.INBOUND));

        List<FlowView> outbound = service.catalog(FlowDirection.OUTBOUND);
        assertEquals(7, outbound.size());
        assertEquals("OUT-01", outbound.get(0).id());
    }

    /**
     * The formats reference lists the main files and the common CSV conventions.
     */
    @Test
    void exposes_the_formats_reference() {
        FlowCatalogService.FormatsReference formats = service.formats();
        assertTrue(formats.files().stream().anyMatch(f -> f.flow().equals("OUT-02")));
        assertTrue(formats.conventions().stream().anyMatch(c -> c.contains("UTF-8")));
    }
}
