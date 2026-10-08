package fr.alptis.remuneration.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the perimeter-overlap logic of {@link ConflictDetector} (H-42): scalar and
 * array criteria, disjoint and shared value sets, empty sets, blank, malformed and non-object
 * perimeters.
 */
class ConflictDetectorScopeTest {

    private final ConflictDetector detector = new ConflictDetector(new ObjectMapper());

    /**
     * A shared value on every common criterion means the perimeters overlap.
     */
    @Test
    void shared_values_overlap() {
        assertTrue(detector.scopesOverlap("{\"product\":[\"X\",\"Y\"]}", "{\"product\":\"X\"}"));
        assertTrue(detector.scopesOverlap("{\"entity\":\"UNEP\"}", "{\"entity\":\"UNEP\"}"));
    }

    /**
     * A criterion with disjoint value sets means the perimeters do not overlap.
     */
    @Test
    void disjoint_values_do_not_overlap() {
        assertFalse(detector.scopesOverlap("{\"product\":[\"X\"]}", "{\"product\":[\"Y\"]}"));
        assertFalse(detector.scopesOverlap("{\"entity\":\"UNEP\"}", "{\"entity\":\"ALPTIS\"}"));
    }

    /**
     * An absent criterion, an empty value set, a blank, a non-object and a malformed perimeter are
     * all treated as "all" and overlap.
     */
    @Test
    void loose_perimeters_overlap() {
        assertTrue(detector.scopesOverlap("{\"product\":[\"X\"]}", "{\"range\":[\"R\"]}"));
        assertTrue(detector.scopesOverlap("{\"product\":[]}", "{\"product\":[\"X\"]}"));
        assertTrue(detector.scopesOverlap("", "{\"product\":[\"X\"]}"));
        assertTrue(detector.scopesOverlap(null, null));
        assertTrue(detector.scopesOverlap("[1,2]", "{\"product\":[\"X\"]}"));
        assertTrue(detector.scopesOverlap("{bad json", "{\"product\":[\"X\"]}"));
    }

    /**
     * Criteria parsing maps a scalar to a one-value set and an array to its value set.
     */
    @Test
    void criteria_parses_scalar_and_array() {
        assertTrue(detector.criteria("{\"product\":\"X\"}").get("product").contains("X"));
        assertTrue(detector.criteria("{\"product\":[\"X\",\"Y\"]}").get("product").contains("Y"));
        // A nested-object criterion value yields no constraint (empty set).
        assertTrue(detector.criteria("{\"product\":{\"nested\":1}}").get("product").isEmpty());
        assertTrue(detector.criteria("42").isEmpty());
    }
}
