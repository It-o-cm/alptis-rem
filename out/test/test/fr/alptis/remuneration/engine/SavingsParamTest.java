package fr.alptis.remuneration.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests of the engine's savings parameter readers (section 6.6): their null, blank,
 * malformed, absent-field and present-field branches, without a database or container.
 */
class SavingsParamTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The text reader returns the value, or null when the parameters or the field are missing, blank
     * or malformed.
     */
    @Test
    void param_text() {
        assertEquals("V", CalculationEngine.paramText(MAPPER, "{\"s\":\"V\"}", "s"));
        assertNull(CalculationEngine.paramText(MAPPER, null, "s"));
        assertNull(CalculationEngine.paramText(MAPPER, "   ", "s"));
        assertNull(CalculationEngine.paramText(MAPPER, "{bad", "s"));
        assertNull(CalculationEngine.paramText(MAPPER, "{}", "s"));
    }

    /**
     * The flag reader returns the value, or false when the parameters or the field are missing or
     * malformed.
     */
    @Test
    void param_flag() {
        assertTrue(CalculationEngine.paramFlag(MAPPER, "{\"f\":true}", "f"));
        assertFalse(CalculationEngine.paramFlag(MAPPER, "{}", "f"));
        assertFalse(CalculationEngine.paramFlag(MAPPER, null, "f"));
    }

    /**
     * The decimal reader returns the value, or null when the parameters or the field are missing,
     * while the zero-defaulting variant returns zero.
     */
    @Test
    void param_decimal() {
        assertEquals(0, CalculationEngine.paramDecimal(MAPPER, "{\"d\":0.5}", "d").compareTo(new BigDecimal("0.5")));
        assertNull(CalculationEngine.paramDecimal(MAPPER, null, "d"));
        assertNull(CalculationEngine.paramDecimal(MAPPER, "{}", "d"));
        assertEquals(0, CalculationEngine.paramDecimalOrZero(MAPPER, "{}", "d").compareTo(BigDecimal.ZERO));
        assertEquals(0, CalculationEngine.paramDecimalOrZero(MAPPER, "{\"d\":0.5}", "d")
                .compareTo(new BigDecimal("0.5")));
    }
}
