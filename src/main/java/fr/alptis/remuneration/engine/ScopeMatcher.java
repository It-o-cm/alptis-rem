package fr.alptis.remuneration.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Tells whether a rule version's perimeter (JSON conjunction of criteria, section 5.4) covers an
 * event (step 2 of section 6.1, P7). A criterion absent from the perimeter means "all"; a criterion
 * present must contain the event's value on that dimension. The perimeter is a conjunction: every
 * criterion it carries must be satisfied.
 */
@ApplicationScoped
public class ScopeMatcher {

    /** Jackson mapper used to read the JSON perimeter. */
    private final ObjectMapper mapper;

    /**
     * Creates the matcher.
     *
     * @param mapper the Jackson mapper
     */
    @Inject
    public ScopeMatcher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Tells whether a perimeter covers an event described by its attributes.
     *
     * @param scopeJson  the perimeter JSON, possibly null (covers everything)
     * @param attributes the event's attribute values by dimension (entity, product, eventType, …)
     * @return {@code true} if the perimeter covers the event
     */
    public boolean matches(String scopeJson, Map<String, String> attributes) {
        if (scopeJson == null || scopeJson.isBlank()) {
            return true;
        }
        JsonNode root;
        try {
            root = mapper.readTree(scopeJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return true;
        }
        if (!root.isObject()) {
            return true;
        }
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            Set<String> allowed = values(entry.getValue());
            if (allowed.isEmpty()) {
                continue;
            }
            String actual = attributes.get(entry.getKey());
            if (actual == null || !allowed.contains(actual)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads a criterion value into a set: a scalar becomes a one-value set, an array its values,
     * anything else an empty set (treated as "all").
     *
     * @param value the criterion value node
     * @return the value set
     */
    private static Set<String> values(JsonNode value) {
        Set<String> out = new HashSet<>();
        if (value.isArray()) {
            value.forEach(v -> out.add(v.asText()));
        } else if (value.isValueNode()) {
            out.add(value.asText());
        }
        return out;
    }
}
