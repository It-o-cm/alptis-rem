package fr.alptis.remuneration.engine;

import fr.alptis.remuneration.domain.RuleFamily;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every calculation family of section 5.6 is discovered as a CDI bean and serves a
 * usable parameter schema (PAR-02).
 */
@QuarkusTest
class FamilyRegistryCdiTest {

    @Inject
    FamilyRegistry registry;

    /**
     * The nine families are registered and each serves a non-empty object schema.
     */
    @Test
    void every_family_is_registered_with_a_schema() {
        assertEquals(RuleFamily.values().length, registry.families().size());
        for (RuleFamily family : RuleFamily.values()) {
            assertTrue(registry.schemaFor(family).contains("\"type\": \"object\""));
            // An empty object is accepted or rejected without error in the engine, never a crash.
            try {
                registry.validate(family, "{}");
            } catch (fr.alptis.remuneration.domain.ValidationException expected) {
                // required properties missing is a legitimate validation outcome
            }
        }
    }
}
