package fr.alptis.remuneration.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import fr.alptis.remuneration.domain.RuleFamily;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.engine.families.CalculationFamily;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry of the calculation families (section 5.6 / PAR-02). It discovers every
 * {@link CalculationFamily} CDI bean, compiles its JSON Schema once, validates a rule version's
 * parameters against the schema of its family (at entry and at import), and serves the schema to the
 * interface so it can build the family form. Adding a family adds a bean; the registry and the engine
 * are untouched.
 */
@ApplicationScoped
public class FamilyRegistry {

    /** The families by code. */
    private final Map<RuleFamily, CalculationFamily> families = new EnumMap<>(RuleFamily.class);

    /** The compiled schemas by code. */
    private final Map<RuleFamily, JsonSchema> schemas = new EnumMap<>(RuleFamily.class);

    /** Jackson mapper used to parse parameter payloads. */
    private final ObjectMapper mapper;

    /**
     * Creates the registry from the discovered CDI beans.
     *
     * @param discovered the discovered family beans
     * @param mapper     the Jackson mapper
     */
    @Inject
    public FamilyRegistry(Instance<CalculationFamily> discovered, ObjectMapper mapper) {
        this(discovered.stream().toList(), mapper);
    }

    /**
     * Creates the registry from a list of families, indexing each and compiling its schema.
     *
     * @param discovered the family beans
     * @param mapper     the Jackson mapper
     */
    FamilyRegistry(List<CalculationFamily> discovered, ObjectMapper mapper) {
        this.mapper = mapper;
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        for (CalculationFamily family : discovered) {
            families.put(family.code(), family);
            schemas.put(family.code(), factory.getSchema(family.parametersSchema()));
        }
    }

    /**
     * Returns the JSON Schema of a family's parameters, for the interface form.
     *
     * @param family the family code
     * @return the JSON Schema string
     * @throws ValidationException if the family is not registered
     */
    public String schemaFor(RuleFamily family) {
        CalculationFamily bean = families.get(family);
        if (bean == null) {
            throw new ValidationException("Famille de calcul inconnue : " + family);
        }
        return bean.parametersSchema();
    }

    /**
     * Returns the registered family codes, in enum order.
     *
     * @return the family codes
     */
    public List<RuleFamily> families() {
        List<RuleFamily> codes = new ArrayList<>(families.keySet());
        codes.sort(java.util.Comparator.naturalOrder());
        return codes;
    }

    /**
     * Validates a parameter payload against its family's schema.
     *
     * @param family        the family code
     * @param parametersJson the parameters as a JSON object, possibly null or blank
     * @throws ValidationException if the family is unknown, the JSON is malformed, or the payload
     *                             violates the schema (the message lists the violations, in French)
     */
    public void validate(RuleFamily family, String parametersJson) {
        JsonSchema schema = schemas.get(family);
        if (schema == null) {
            throw new ValidationException("Famille de calcul inconnue : " + family);
        }
        JsonNode node = parse(parametersJson);
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            String detail = errors.stream().map(ValidationMessage::getMessage).sorted()
                    .collect(Collectors.joining(" ; "));
            throw new ValidationException("Paramètres invalides pour la famille " + family + " : " + detail);
        }
    }

    /**
     * Parses a parameter payload into a JSON node, treating null or blank as an empty object.
     *
     * @param parametersJson the raw payload
     * @return the parsed node
     * @throws ValidationException if the payload is not valid JSON
     */
    private JsonNode parse(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(parametersJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ValidationException("Paramètres illisibles (JSON invalide).");
        }
    }
}
