package fr.alptis.remuneration.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms a free (non-pivot) file into pivot rows using a parameterised mapping (ING-02): column
 * correspondence, default values, transcoding tables, a simple split expression, a line filter and
 * derived rows. It covers the UMR mapping of section 9.4 (CR-37/38): a line whose entity part is
 * not kept is filtered out, and a second {@code INSURER_COMMISSION} row is derived from the
 * precomputed columns.
 *
 * <p>The mapping definition is JSON:</p>
 * <pre>
 * {
 *   "filter":   {"column":"NOM_MUTUELLE","equals":"ALPTIS"},
 *   "fields":   {"source_key":{"column":"CODE_MOUVEMENT"},
 *                "event_type":{"column":"TYPE_MOUVEMENT","transcode":{"VI":"INITIAL_PAYMENT"}},
 *                "event_date":{"column":"DATE_MOUVEMENT"},
 *                "partner_code":{"column":"CODE_POINT_VENTE","split":{"regex":"_P|-","index":1,"prefix":"P"}}},
 *   "derived":  [{"when":{"column":"MONTANT_COMMISSION","present":true},
 *                 "fields":{"source_key":{"column":"CODE_MOUVEMENT","suffix":"-COM"},
 *                           "event_type":{"const":"INSURER_COMMISSION"},
 *                           "precomputed_amount":{"column":"MONTANT_COMMISSION"}}}]
 * }
 * </pre>
 */
@ApplicationScoped
public class MappingEngine {

    /** JSON parser for the mapping definition. */
    private final ObjectMapper mapper;

    /**
     * Result of a mapping run: one group of pivot rows per kept source line (a primary row and any
     * derived rows), and the number of filtered-out source lines.
     *
     * @param groups   the produced pivot-row groups, one per kept source line
     * @param filtered the number of source lines dropped by the filter
     */
    public record Result(List<List<Map<String, String>>> groups, int filtered) {
    }

    /**
     * Creates the engine.
     *
     * @param mapper the JSON object mapper
     */
    @Inject
    public MappingEngine(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Applies a mapping definition to a source file.
     *
     * @param definitionJson the mapping definition (JSON)
     * @param source         the parsed source file
     * @return the produced pivot rows and the count of filtered lines
     * @throws ValidationException if the definition is not valid JSON
     */
    public Result apply(String definitionJson, CsvFile source) {
        JsonNode definition = parse(definitionJson);
        JsonNode filter = definition.get("filter");
        JsonNode fields = definition.get("fields");
        JsonNode derived = definition.get("derived");

        List<List<Map<String, String>>> groups = new ArrayList<>();
        int filtered = 0;
        for (Map<String, String> sourceRow : source.rows()) {
            if (!passesFilter(filter, sourceRow)) {
                filtered++;
                continue;
            }
            List<Map<String, String>> group = new ArrayList<>();
            group.add(mapFields(fields, sourceRow));
            if (derived != null) {
                for (JsonNode rule : derived) {
                    if (passesWhen(rule.get("when"), sourceRow)) {
                        group.add(mapFields(rule.get("fields"), sourceRow));
                    }
                }
            }
            groups.add(group);
        }
        return new Result(groups, filtered);
    }

    /**
     * Parses the mapping definition JSON.
     *
     * @param definitionJson the JSON text
     * @return the parsed tree
     */
    private JsonNode parse(String definitionJson) {
        try {
            return mapper.readTree(definitionJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ValidationException("Définition de mapping invalide : " + e.getOriginalMessage());
        }
    }

    /**
     * Tells whether a source row passes the optional entity-part filter.
     *
     * @param filter    the filter node, or null
     * @param sourceRow the source row
     * @return {@code true} if the row is kept
     */
    private static boolean passesFilter(JsonNode filter, Map<String, String> sourceRow) {
        if (filter == null) {
            return true;
        }
        String actual = value(sourceRow, filter.get("column").asText());
        return actual.equals(filter.get("equals").asText());
    }

    /**
     * Evaluates a derived-row condition.
     *
     * @param when      the condition node, or null (always true)
     * @param sourceRow the source row
     * @return {@code true} if the derived row must be produced
     */
    private static boolean passesWhen(JsonNode when, Map<String, String> sourceRow) {
        if (when == null) {
            return true;
        }
        return !value(sourceRow, when.get("column").asText()).isEmpty();
    }

    /**
     * Builds a pivot row from a field-mapping node.
     *
     * @param fields    the fields node
     * @param sourceRow the source row
     * @return the produced pivot row
     */
    private static Map<String, String> mapFields(JsonNode fields, Map<String, String> sourceRow) {
        Map<String, String> row = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            row.put(entry.getKey(), resolveField(entry.getValue(), sourceRow));
        }
        return row;
    }

    /**
     * Resolves one target field value from its mapping rule.
     *
     * @param rule      the field rule
     * @param sourceRow the source row
     * @return the resolved value
     */
    private static String resolveField(JsonNode rule, Map<String, String> sourceRow) {
        if (rule.has("const")) {
            return rule.get("const").asText();
        }
        String raw = value(sourceRow, rule.get("column").asText());
        if (rule.has("split")) {
            raw = split(rule.get("split"), raw);
        }
        if (rule.has("transcode")) {
            JsonNode mapped = rule.get("transcode").get(raw);
            raw = mapped == null ? raw : mapped.asText();
        }
        if (rule.has("default") && raw.isEmpty()) {
            raw = rule.get("default").asText();
        }
        if (rule.has("suffix")) {
            raw = raw + rule.get("suffix").asText();
        }
        return raw;
    }

    /**
     * Splits a value by a regex and returns one piece, optionally prefixed.
     *
     * @param split the split rule ({@code regex}, {@code index}, optional {@code prefix})
     * @param value the value to split
     * @return the extracted piece
     */
    private static String split(JsonNode split, String value) {
        String[] parts = value.split(split.get("regex").asText());
        int index = split.get("index").asInt();
        String piece = index < parts.length ? parts[index] : "";
        String prefix = split.has("prefix") ? split.get("prefix").asText() : "";
        return prefix + piece;
    }

    /**
     * Returns a source column value, or empty.
     *
     * @param row    the source row
     * @param column the column name
     * @return the value, never null
     */
    private static String value(Map<String, String> row, String column) {
        String raw = row.get(column);
        return raw == null ? "" : raw;
    }
}
