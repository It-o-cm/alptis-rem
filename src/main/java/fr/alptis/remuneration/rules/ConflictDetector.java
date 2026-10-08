package fr.alptis.remuneration.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects the rule-version conflicts of section 6.11: two live versions of the same exclusion group
 * and the same priority whose perimeters and validity periods overlap. The approval of a version is
 * refused while such a conflict exists (PAR-05). Two perimeters overlap unless there is at least one
 * criterion on which they carry value sets with nothing in common (H-42); an absent criterion means
 * "all" and always overlaps.
 */
@ApplicationScoped
public class ConflictDetector {

    /** Jackson mapper used to compare the JSON perimeters. */
    private final ObjectMapper mapper;

    /**
     * Creates the detector.
     *
     * @param mapper the Jackson mapper
     */
    @Inject
    public ConflictDetector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Returns the live versions that conflict with the given candidate, within its entity.
     *
     * @param candidate the version being approved
     * @return the conflicting versions (empty when there is no conflict)
     */
    public List<RuleVersion> conflicts(RuleVersion candidate) {
        List<RuleVersion> result = new ArrayList<>();
        List<RuleVersion> others = RuleVersion.list(
                "rule.plan.entity = ?1 and status in ?2",
                candidate.rule.plan.entity,
                List.of(VersionStatus.APPROVED, VersionStatus.ACTIVE));
        for (RuleVersion other : others) {
            if (other.getId().equals(candidate.getId())) {
                continue;
            }
            if (sameGroup(candidate, other) && samePriority(candidate, other)
                    && datesOverlap(candidate, other) && scopesOverlap(candidate.scope, other.scope)) {
                result.add(other);
            }
        }
        return result;
    }

    /**
     * Tells whether two versions share the same exclusion group.
     *
     * @param a the first version
     * @param b the second version
     * @return {@code true} if the exclusion groups are equal
     */
    private static boolean sameGroup(RuleVersion a, RuleVersion b) {
        return java.util.Objects.equals(a.exclusionGroup, b.exclusionGroup);
    }

    /**
     * Tells whether two versions share the same priority.
     *
     * @param a the first version
     * @param b the second version
     * @return {@code true} if the priorities are equal
     */
    private static boolean samePriority(RuleVersion a, RuleVersion b) {
        return java.util.Objects.equals(a.priority, b.priority);
    }

    /**
     * Tells whether two validity periods overlap, treating null bounds as open-ended.
     *
     * @param a the first version
     * @param b the second version
     * @return {@code true} if the periods intersect
     */
    private static boolean datesOverlap(RuleVersion a, RuleVersion b) {
        LocalDate aFrom = a.validFrom;
        LocalDate aTo = a.validTo;
        LocalDate bFrom = b.validFrom;
        LocalDate bTo = b.validTo;
        boolean aStartsAfterBEnds = aFrom != null && bTo != null && aFrom.isAfter(bTo);
        boolean bStartsAfterAEnds = bFrom != null && aTo != null && bFrom.isAfter(aTo);
        return !aStartsAfterBEnds && !bStartsAfterAEnds;
    }

    /**
     * Tells whether two JSON perimeters overlap. They overlap unless some shared criterion carries
     * disjoint value sets.
     *
     * @param scopeA the first perimeter (JSON), possibly null or blank
     * @param scopeB the second perimeter (JSON), possibly null or blank
     * @return {@code true} if the perimeters overlap
     */
    boolean scopesOverlap(String scopeA, String scopeB) {
        Map<String, Set<String>> a = criteria(scopeA);
        Map<String, Set<String>> b = criteria(scopeB);
        for (Map.Entry<String, Set<String>> entry : a.entrySet()) {
            Set<String> other = b.get(entry.getKey());
            if (other != null && disjoint(entry.getValue(), other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells whether two non-empty value sets have nothing in common.
     *
     * @param one   the first value set
     * @param other the second value set
     * @return {@code true} if both are non-empty and disjoint
     */
    private static boolean disjoint(Set<String> one, Set<String> other) {
        if (one.isEmpty() || other.isEmpty()) {
            return false;
        }
        for (String value : one) {
            if (other.contains(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a JSON perimeter into criterion-to-value-set pairs; a scalar becomes a one-value set,
     * an array its set of values, anything else an empty set (treated as "all").
     *
     * @param scopeJson the perimeter JSON, possibly null or blank
     * @return the criteria map, never null
     */
    Map<String, Set<String>> criteria(String scopeJson) {
        Map<String, Set<String>> out = new java.util.LinkedHashMap<>();
        if (scopeJson == null || scopeJson.isBlank()) {
            return out;
        }
        JsonNode root;
        try {
            root = mapper.readTree(scopeJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return out;
        }
        if (!root.isObject()) {
            return out;
        }
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            Set<String> values = new HashSet<>();
            JsonNode value = entry.getValue();
            if (value.isArray()) {
                value.forEach(v -> values.add(v.asText()));
            } else if (value.isValueNode()) {
                values.add(value.asText());
            }
            out.put(entry.getKey(), values);
        }
        return out;
    }
}
