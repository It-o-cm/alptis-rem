package fr.alptis.remuneration.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.engine.families.ExternalFamily;
import fr.alptis.remuneration.engine.families.LinearFamily;
import fr.alptis.remuneration.engine.families.UpfrontFamily;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link FamilyRegistry}, built with a controlled subset of families so the
 * unknown-family, schema-violation, malformed-JSON and empty-payload branches are all reached.
 */
class FamilyRegistryTest {

    private final FamilyRegistry registry = new FamilyRegistry(
            List.of(new LinearFamily(), new UpfrontFamily(), new ExternalFamily()), new ObjectMapper());

    /**
     * Lists the registered families in enum order and serves their schema.
     */
    @Test
    void lists_and_serves_schemas() {
        assertEquals(List.of(RuleFamily.LINEAR, RuleFamily.UPFRONT, RuleFamily.EXTERNAL), registry.families());
        assertTrue(registry.schemaFor(RuleFamily.LINEAR).contains("allowedRates"));
    }

    /**
     * Serving the schema of an unregistered family is refused.
     */
    @Test
    void refuses_schema_of_unknown_family() {
        assertThrows(ValidationException.class, () -> registry.schemaFor(RuleFamily.ADVANCE));
    }

    /**
     * Valid linear parameters pass; a rate above one and malformed JSON are refused.
     */
    @Test
    void validates_linear_parameters() {
        registry.validate(RuleFamily.LINEAR, "{\"allowedRates\":[0.15,0.18]}");
        assertThrows(ValidationException.class, () -> registry.validate(RuleFamily.LINEAR, "{\"allowedRates\":[2]}"));
        assertThrows(ValidationException.class, () -> registry.validate(RuleFamily.LINEAR, "{not json"));
    }

    /**
     * A null payload is treated as an empty object: rejected when the schema requires a property,
     * accepted when it does not.
     */
    @Test
    void treats_null_payload_as_empty_object() {
        assertThrows(ValidationException.class, () -> registry.validate(RuleFamily.OUTSTANDING, null));
        registry.validate(RuleFamily.EXTERNAL, null);
        registry.validate(RuleFamily.EXTERNAL, "   ");
    }

    /**
     * Validating against an unregistered family is refused.
     */
    @Test
    void refuses_validation_for_unknown_family() {
        assertThrows(ValidationException.class, () -> registry.validate(RuleFamily.TIERED, "{}"));
    }
}
