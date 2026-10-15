package fr.alptis.remuneration.salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests of the plan-parameter helpers of {@link SalesForceService}: parsing a payload into a
 * JSON node (null, blank, valid and malformed) and reading a decimal field (present, missing, JSON
 * null). They run without a container so every branch, including the malformed-JSON guard, is covered.
 */
class SalesForceParamsTest {

    /** The shared JSON mapper. */
    private final ObjectMapper mapper = new ObjectMapper();

    /** A null or blank payload yields an empty object; a valid payload is parsed. */
    @Test
    void reads_null_blank_and_valid_parameters() {
        assertTrue(SalesForceService.readParameters(mapper, null).isEmpty());
        assertTrue(SalesForceService.readParameters(mapper, "   ").isEmpty());
        JsonNode node = SalesForceService.readParameters(mapper, "{\"criterionAmount\":666.67}");
        assertEquals(0, SalesForceService.decimalParam(node, "criterionAmount").compareTo(new BigDecimal("666.67")));
    }

    /** A malformed payload is rejected. */
    @Test
    void rejects_malformed_parameters() {
        assertThrows(ValidationException.class, () -> SalesForceService.readParameters(mapper, "{not-json"));
    }

    /** A missing field and a JSON null both default to zero. */
    @Test
    void missing_and_null_fields_default_to_zero() {
        JsonNode node = SalesForceService.readParameters(mapper, "{\"present\":5,\"empty\":null}");
        assertEquals(0, SalesForceService.decimalParam(node, "present").compareTo(new BigDecimal("5")));
        assertEquals(0, SalesForceService.decimalParam(node, "absent").compareTo(BigDecimal.ZERO));
        assertEquals(0, SalesForceService.decimalParam(node, "empty").compareTo(BigDecimal.ZERO));
    }
}
