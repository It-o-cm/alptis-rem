package fr.alptis.remuneration.security;

import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.RoleMapping;
import fr.alptis.remuneration.domain.ValidationException;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import fr.alptis.remuneration.domain.ResourceNotFoundException;

import java.util.List;

/**
 * Manages the directory-group to role correspondence (section 3), the source from which users'
 * roles and visible entities are deduced (requirement ADM-01). Reserved to the technical
 * administrator; every change is recorded in the audit trail.
 */
@ApplicationScoped
public class RoleMappingAdminService {

    /** The current user, used as the audit author. */
    private final CurrentUser currentUser;

    /** The audit trail. */
    private final AuditService audit;

    /**
     * Creates the service.
     *
     * @param currentUser the current user
     * @param audit       the audit trail
     */
    @Inject
    public RoleMappingAdminService(CurrentUser currentUser, AuditService audit) {
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /**
     * Lists every role mapping, ordered by directory group then role.
     *
     * @return the role mappings
     */
    @Transactional
    public List<RoleMappingView> list() {
        List<RoleMapping> mappings = RoleMapping.listAll(Sort.by("directoryGroup").and("role"));
        return mappings.stream().map(RoleMappingAdminService::toView).toList();
    }

    /**
     * Creates a role mapping.
     *
     * @param directoryGroup the directory group
     * @param roleName       the role name
     * @param entityCode     the entity code the role is scoped to, or {@code null} for every entity
     * @return the created mapping view
     * @throws ValidationException if the role name is unknown
     * @throws ResourceNotFoundException   if the entity code is given but unknown
     * @throws ConflictException   if the mapping already exists
     */
    @Transactional
    public RoleMappingView create(String directoryGroup, String roleName, String entityCode) {
        Role role = parseRole(roleName);
        LegalEntity entity = resolveEntity(entityCode);
        if (findMapping(directoryGroup, role, entity) != null) {
            throw new ConflictException("Cette correspondance existe déjà.");
        }
        RoleMapping mapping = new RoleMapping();
        mapping.directoryGroup = directoryGroup;
        mapping.role = role;
        mapping.entity = entity;
        mapping.persist();
        audit.record(currentUser.username(), entity, "CREATE_ROLE_MAPPING", "RoleMapping",
                mapping.getId(), null, snapshot(mapping));
        return toView(mapping);
    }

    /**
     * Deletes a role mapping by its natural key.
     *
     * @param directoryGroup the directory group
     * @param roleName       the role name
     * @param entityCode     the entity code, or {@code null} for the every-entity mapping
     * @throws ValidationException if the role name is unknown
     * @throws ResourceNotFoundException   if the mapping does not exist
     */
    @Transactional
    public void delete(String directoryGroup, String roleName, String entityCode) {
        Role role = parseRole(roleName);
        LegalEntity entity = resolveEntity(entityCode);
        RoleMapping mapping = findMapping(directoryGroup, role, entity);
        if (mapping == null) {
            throw new ResourceNotFoundException("Correspondance introuvable.");
        }
        audit.record(currentUser.username(), entity, "DELETE_ROLE_MAPPING", "RoleMapping",
                mapping.getId(), snapshot(mapping), null);
        mapping.delete();
    }

    /**
     * Parses a role name into a {@link Role}.
     *
     * @param roleName the role name
     * @return the role
     * @throws ValidationException if the name is not a known role
     */
    private static Role parseRole(String roleName) {
        if (roleName == null) {
            throw new ValidationException("Rôle inconnu : " + roleName);
        }
        try {
            return Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Rôle inconnu : " + roleName);
        }
    }

    /**
     * Resolves an entity code to a legal entity, or {@code null}.
     *
     * @param entityCode the entity code, possibly null or blank
     * @return the entity, or {@code null} for the every-entity scope
     * @throws ResourceNotFoundException if the code is given but unknown
     */
    private static LegalEntity resolveEntity(String entityCode) {
        if (entityCode == null || entityCode.isBlank()) {
            return null;
        }
        LegalEntity entity = LegalEntity.find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité introuvable : " + entityCode);
        }
        return entity;
    }

    /**
     * Finds a mapping by its natural key, handling the every-entity (null) case.
     *
     * @param directoryGroup the directory group
     * @param role           the role
     * @param entity         the entity, or {@code null}
     * @return the mapping, or {@code null}
     */
    private static RoleMapping findMapping(String directoryGroup, Role role, LegalEntity entity) {
        if (entity == null) {
            return RoleMapping.find("directoryGroup = ?1 and role = ?2 and entity is null",
                    directoryGroup, role).firstResult();
        }
        return RoleMapping.find("directoryGroup = ?1 and role = ?2 and entity = ?3",
                directoryGroup, role, entity).firstResult();
    }

    /**
     * Builds the exchange view of a mapping.
     *
     * @param mapping the mapping
     * @return its view
     */
    private static RoleMappingView toView(RoleMapping mapping) {
        return new RoleMappingView(mapping.directoryGroup, mapping.role.name(),
                mapping.entity == null ? null : mapping.entity.code);
    }

    /**
     * Builds a compact JSON snapshot of a mapping for the audit trail.
     *
     * @param mapping the mapping
     * @return the JSON snapshot
     */
    private static String snapshot(RoleMapping mapping) {
        String entityCode = mapping.entity == null ? "null" : "\"" + mapping.entity.code + "\"";
        return "{\"directoryGroup\":\"" + mapping.directoryGroup + "\",\"role\":\"" + mapping.role.name()
                + "\",\"entity\":" + entityCode + "}";
    }

    /**
     * Exchange view of a role mapping.
     *
     * @param directoryGroup the directory group
     * @param role           the role name
     * @param entityCode     the entity code, or {@code null} for every entity
     */
    public record RoleMappingView(String directoryGroup, String role, String entityCode) {
    }
}
