package fr.alptis.remuneration.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link ScopeMatcher} and the parameters-array reader: the perimeter-matching
 * branches of section 6.1 (P7), including absent, empty, scalar and array criteria, a non-matching
 * value, and null, blank, non-object and malformed perimeters.
 */
class ScopeMatcherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ScopeMatcher matcher = new ScopeMatcher(MAPPER);

    /** A perimeter with no criterion (null, blank, non-object or malformed) covers everything. */
    @Test
    void loose_perimeters_cover_everything() {
        Map<String, String> attributes = Map.of("product", "X");
        assertTrue(matcher.matches(null, attributes));
        assertTrue(matcher.matches("", attributes));
        assertTrue(matcher.matches("[1,2]", attributes));
        assertTrue(matcher.matches("{bad json", attributes));
    }

    /** An array and a scalar criterion both match when the attribute is one of their values. */
    @Test
    void matches_array_and_scalar_criteria() {
        assertTrue(matcher.matches("{\"product\":[\"X\",\"Y\"]}", Map.of("product", "Y")));
        assertTrue(matcher.matches("{\"entity\":\"UNEP\"}", Map.of("entity", "UNEP")));
    }

    /** An empty value set, or a non-scalar criterion value, imposes no constraint on its dimension. */
    @Test
    void empty_value_set_is_no_constraint() {
        assertTrue(matcher.matches("{\"product\":[]}", Map.of("product", "X")));
        assertTrue(matcher.matches("{\"product\":{\"nested\":1}}", Map.of("product", "X")));
    }

    /** A criterion whose dimension the event lacks, or whose value differs, does not match. */
    @Test
    void absent_or_different_value_does_not_match() {
        assertFalse(matcher.matches("{\"college\":[\"EXECUTIVE\"]}", Map.of("product", "X")));
        assertFalse(matcher.matches("{\"product\":[\"X\"]}", Map.of("product", "Z")));
    }

    /** The parameters-array reader returns the elements, or an empty list for the edge cases. */
    @Test
    void reads_parameter_arrays() {
        List<JsonNode> rates = CalculationEngine.readArray(MAPPER, "{\"allowedRates\":[0.15,0.18]}", "allowedRates");
        assertEquals(2, rates.size());
        assertTrue(CalculationEngine.readArray(MAPPER, null, "allowedRates").isEmpty());
        assertTrue(CalculationEngine.readArray(MAPPER, "   ", "allowedRates").isEmpty());
        assertTrue(CalculationEngine.readArray(MAPPER, "{}", "allowedRates").isEmpty());
        assertTrue(CalculationEngine.readArray(MAPPER, "{\"allowedRates\":5}", "allowedRates").isEmpty());
        assertTrue(CalculationEngine.readArray(MAPPER, "{bad", "allowedRates").isEmpty());
    }

    /** The object reader returns the tree, or null for a null, blank or malformed string. */
    @Test
    void reads_json_objects() {
        assertTrue(CalculationEngine.readObject(MAPPER, "{\"a\":1}").has("a"));
        org.junit.jupiter.api.Assertions.assertNull(CalculationEngine.readObject(MAPPER, null));
        org.junit.jupiter.api.Assertions.assertNull(CalculationEngine.readObject(MAPPER, "   "));
        org.junit.jupiter.api.Assertions.assertNull(CalculationEngine.readObject(MAPPER, "{bad"));
    }
}
