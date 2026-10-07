package fr.alptis.remuneration.security;

import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Role;
import fr.alptis.remuneration.domain.RoleMapping;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds, at startup outside production, the referential data the security layer needs: the group's
 * legal entities and the directory-group to role correspondence (section 3). In dev the in-memory
 * database is recreated at each launch; this seed is idempotent either way.
 */
@ApplicationScoped
@UnlessBuildProfile("prod")
public class SecurityBootstrap {

    /** The group's legal entities: code and name. */
    private static final String[][] ENTITIES = {
        {"ALPTIS_ASSURANCES", "Alptis Assurances"},
        {"UNEP", "UNEP"},
        {"PROMANTIS_CONSEIL", "Promantis Conseil"},
        {"INSURED_SERVICES", "Insured Services"},
    };

    /**
     * Seeds entities and role mappings when the application starts.
     *
     * @param event the startup event
     */
    void onStart(@Observes StartupEvent event) {
        seed();
    }

    /**
     * Performs the idempotent seed in a single transaction.
     */
    @Transactional
    public void seed() {
        if (LegalEntity.count() == 0) {
            for (String[] def : ENTITIES) {
                LegalEntity entity = new LegalEntity();
                entity.code = def[0];
                entity.name = def[1];
                entity.minPaymentAmount = new BigDecimal("10.00");
                entity.advanceSettlementDays = 360;
                entity.advanceSettlementTolerance = new BigDecimal("1.00");
                entity.paymentCalendar = "{\"freezeDay\":\"last\",\"paymentWindow\":[5,10]}";
                entity.persist();
            }
        }
        if (RoleMapping.count() == 0) {
            mapping("grp-it", Role.IT_ADMIN, null);
            mapping("grp-audit", Role.AUDITOR, null);
            mapping("grp-group-validator", Role.BUSINESS_VALIDATOR, null);
            for (String[] scope : new String[][]{{"alptis", "ALPTIS_ASSURANCES"}, {"unep", "UNEP"}}) {
                String suffix = scope[0];
                String code = scope[1];
                mapping("grp-ba-" + suffix, Role.BUSINESS_ADMIN, code);
                mapping("grp-op-" + suffix, Role.OPERATOR, code);
                mapping("grp-bv-" + suffix, Role.BUSINESS_VALIDATOR, code);
                mapping("grp-fc-" + suffix, Role.FINANCE_CONTROLLER, code);
                mapping("grp-vw-" + suffix, Role.VIEWER, code);
                mapping("grp-sm-" + suffix, Role.SALES_MANAGER, code);
                mapping("grp-sr-" + suffix, Role.SALES_REP, code);
            }
        }
    }

    /**
     * Persists a single directory-group to role mapping.
     *
     * @param group      the directory group
     * @param role       the granted role
     * @param entityCode the entity code the role is scoped to, or {@code null} for every entity
     */
    private void mapping(String group, Role role, String entityCode) {
        RoleMapping rm = new RoleMapping();
        rm.directoryGroup = group;
        rm.role = role;
        if (entityCode != null) {
            rm.entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        }
        rm.persist();
    }

    /**
     * Returns the group's legal entity codes, in seeding order.
     *
     * @return the entity codes
     */
    public static List<String> entityCodes() {
        return List.of(ENTITIES[0][0], ENTITIES[1][0], ENTITIES[2][0], ENTITIES[3][0]);
    }
}
