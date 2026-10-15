package fr.alptis.remuneration.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of the dev-only demo bootstrap: its startup callback delegates to the demo service.
 * Instantiated directly so the {@code dev}-only bean is covered without starting a container.
 */
class DemoDataBootstrapTest {

    /**
     * The startup callback loads the demonstration dataset.
     */
    @Test
    void startup_loads_the_demo_dataset() {
        boolean[] loaded = {false};
        DemoDataService service = new DemoDataService(null, null, null, null, null) {
            @Override
            public void load() {
                loaded[0] = true;
            }
        };
        new DemoDataBootstrap(service).onStart(null);
        assertTrue(loaded[0]);
    }
}
