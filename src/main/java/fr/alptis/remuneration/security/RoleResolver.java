package fr.alptis.remuneration.security;

import fr.alptis.remuneration.domain.RoleMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deduces the roles and visible entities of a user from its directory groups, by reading the
 * editable {@code role_mapping} table (section 3). Changing a mapping therefore changes a user's
 * rights at the next login (requirement ADM-01).
 */
@ApplicationScoped
public class RoleResolver {

    /**
     * Grants deduced for a user: its role names and the entity codes it may see (a single
     * {@code *} meaning every entity).
     *
     * @param roles    the granted role names
     * @param entities the visible entity codes
     */
    public record Grants(Set<String> roles, List<String> entities) {
    }

    /**
     * Resolves the grants for the given directory groups.
     *
     * @param groups the user's directory groups
     * @return the deduced roles and visible entities
     */
    @Transactional
    public Grants resolve(List<String> groups) {
        Set<String> roles = new LinkedHashSet<>();
        Set<String> entities = new LinkedHashSet<>();
        if (!groups.isEmpty()) {
            List<RoleMapping> mappings = RoleMapping.list("directoryGroup in ?1", groups);
            for (RoleMapping mapping : mappings) {
                roles.add(mapping.role.name());
                if (mapping.entity == null) {
                    entities.add("*");
                } else {
                    entities.add(mapping.entity.code);
                }
            }
        }
        return new Grants(roles, new ArrayList<>(entities));
    }
}
