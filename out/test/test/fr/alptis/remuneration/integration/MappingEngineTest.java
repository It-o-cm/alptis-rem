package fr.alptis.remuneration.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link MappingEngine}, covering the UMR-style mapping of section 9.4: filter,
 * column, const, split, transcode, default, suffix, derived rows.
 */
class MappingEngineTest {

    private final MappingEngine engine = new MappingEngine(new ObjectMapper());

    private static final String UMR_DEFINITION = """
            {
              "filter": {"column": "NOM_MUTUELLE", "equals": "ALPTIS"},
              "fields": {
                "source_key": {"column": "CODE_MOUVEMENT"},
                "event_type": {"column": "TYPE_MOUVEMENT", "transcode": {"VI": "INITIAL_PAYMENT"}},
                "partner_code": {"column": "CODE_POINT_VENTE", "split": {"regex": "_P|-", "index": 1, "prefix": "P"}},
                "profile": {"column": "PROFIL", "default": "STD"},
                "label": {"const": "UMR"}
              },
              "derived": [{
                "when": {"column": "MONTANT_COMMISSION"},
                "fields": {
                  "source_key": {"column": "CODE_MOUVEMENT", "suffix": "-COM"},
                  "event_type": {"const": "INSURER_COMMISSION"},
                  "precomputed_amount": {"column": "MONTANT_COMMISSION"}
                }
              }]
            }
            """;

    /**
     * Applies the UMR mapping: the UMR part is filtered out, the Alptis part is mapped, and a
     * derived INSURER_COMMISSION row is produced when a commission amount is present.
     */
    @Test
    void applies_the_umr_mapping() {
        String source = "CODE_MOUVEMENT;TYPE_MOUVEMENT;CODE_POINT_VENTE;NOM_MUTUELLE;MONTANT_COMMISSION;PROFIL\n"
                + "M1;VI;300516_P75942-72717;ALPTIS;0.24;\n"
                + "M1b;VI;300516_P75942-72717;UMR;0.19;\n"
                + "M2;VI;300516_P75942-72717;ALPTIS;;FR\n";

        MappingEngine.Result result = engine.apply(UMR_DEFINITION, CsvFile.parse(source));

        assertEquals(1, result.filtered());
        assertEquals(2, result.groups().size());

        List<Map<String, String>> first = result.groups().get(0);
        assertEquals(2, first.size());
        assertEquals("INITIAL_PAYMENT", first.get(0).get("event_type"));
        assertEquals("P75942", first.get(0).get("partner_code"));
        assertEquals("STD", first.get(0).get("profile"));
        assertEquals("UMR", first.get(0).get("label"));
        assertEquals("INSURER_COMMISSION", first.get(1).get("event_type"));
        assertEquals("M1-COM", first.get(1).get("source_key"));
        assertEquals("0.24", first.get(1).get("precomputed_amount"));

        // Second kept line has no commission amount: no derived row, and the explicit profile is kept.
        List<Map<String, String>> second = result.groups().get(1);
        assertEquals(1, second.size());
        assertEquals("FR", second.get(0).get("profile"));
    }

    /**
     * Without a filter, every line is kept; a transcode miss leaves the value unchanged and a split
     * index beyond the parts yields an empty piece.
     */
    @Test
    void maps_without_filter_and_handles_edge_cases() {
        String definition = """
                {
                  "fields": {
                    "event_type": {"column": "T", "transcode": {"X": "Y"}},
                    "tail": {"column": "C", "split": {"regex": "-", "index": 5}}
                  }
                }
                """;
        String source = "T;C\nKEEP;a-b\n";

        MappingEngine.Result result = engine.apply(definition, CsvFile.parse(source));

        assertEquals(0, result.filtered());
        assertEquals(1, result.groups().size());
        assertEquals("KEEP", result.groups().get(0).get(0).get("event_type"));
        assertEquals("", result.groups().get(0).get(0).get("tail"));
    }

    /**
     * A derived row with no {@code when} is always produced; a split covers an in-range index with
     * no prefix and an out-of-range index with a prefix.
     */
    @Test
    void derived_without_when_and_split_variants() {
        String definition = """
                {
                  "fields": {
                    "a": {"column": "C", "split": {"regex": "-", "index": 0}},
                    "b": {"column": "C", "split": {"regex": "-", "index": 9, "prefix": "X"}},
                    "z": {"column": "ABSENT_COLUMN"}
                  },
                  "derived": [{"fields": {"d": {"const": "D"}}}]
                }
                """;

        MappingEngine.Result result = engine.apply(definition, CsvFile.parse("C\na-b\n"));

        assertEquals(1, result.groups().size());
        List<Map<String, String>> group = result.groups().get(0);
        assertEquals(2, group.size());
        assertEquals("a", group.get(0).get("a"));
        assertEquals("X", group.get(0).get("b"));
        assertEquals("", group.get(0).get("z"));
        assertEquals("D", group.get(1).get("d"));
    }

    /**
     * An invalid mapping definition is rejected.
     */
    @Test
    void rejects_an_invalid_definition() {
        assertThrows(ValidationException.class, () -> engine.apply("{not json", CsvFile.parse("a\n1\n")));
    }
}
