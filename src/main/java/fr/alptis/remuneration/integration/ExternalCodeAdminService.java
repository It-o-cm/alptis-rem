package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.ExternalCode;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Adds external-code correspondences (REF-04). A correspondence maps an object's identifier in a source
 * system to the internal object; an unknown code holds an event instead of rejecting it, and the next
 * calculation releases the held events once the correspondence is added (INT-22). Scoped by entity;
 * additions are audited.
 */
@ApplicationScoped
public class ExternalCodeAdminService {

    private final EntityScope scope;
    private final CurrentUser currentUser;
    private final AuditService audit;

    /**
     * Creates the service.
     *
     * @param scope       the entity partition
     * @param currentUser the current user
     * @param audit       the audit trail
     */
    @Inject
    public ExternalCodeAdminService(EntityScope scope, CurrentUser currentUser, AuditService audit) {
        this.scope = scope;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /**
     * Maps a source system's contract identifier to an internal contract (REF-04, INT-22). Once added,
     * the next calculation re-resolves the events held on that unknown reference and releases them.
     *
     * @param entityCode             the entity the contract belongs to
     * @param sourceSystem           the source system the external code is expressed in
     * @param externalCode           the contract identifier in that source system
     * @param internalContractNumber the internal contract number the code maps to
     * @return the created correspondence view
     * @throws ResourceNotFoundException if the entity is out of scope
     * @throws ValidationException       if a field is blank, the internal contract is unknown, or the
     *                                   correspondence already exists
     */
    @Transactional
    public ReferentialQueryService.ExternalCodeView mapContract(String entityCode, String sourceSystem,
            String externalCode, String internalContractNumber) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null || !scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        if (isBlank(sourceSystem) || isBlank(externalCode) || isBlank(internalContractNumber)) {
            throw new ValidationException("Le système source, le code externe et le contrat interne sont exigés");
        }
        Contract contract = Contract.<Contract>find("entity = ?1 and contractNumber = ?2",
                entity, internalContractNumber).firstResult();
        if (contract == null) {
            throw new ValidationException("Contrat interne introuvable : " + internalContractNumber);
        }
        boolean exists = ExternalCode.count("objectType = ?1 and sourceSystem = ?2 and externalCode = ?3",
                "CONTRACT", sourceSystem, externalCode) > 0;
        if (exists) {
            throw new ValidationException("Correspondance déjà définie pour " + sourceSystem + "/" + externalCode);
        }
        ExternalCode code = new ExternalCode();
        code.objectType = "CONTRACT";
        code.objectId = contract.getId();
        code.sourceSystem = sourceSystem;
        code.externalCode = externalCode;
        code.persist();
        audit.record(currentUser.username(), entity, "MAP_EXTERNAL_CODE", "ExternalCode", code.getId(), null,
                "{\"objectType\":\"CONTRACT\",\"sourceSystem\":\"" + sourceSystem + "\",\"externalCode\":\""
                        + externalCode + "\"}");
        return new ReferentialQueryService.ExternalCodeView("CONTRACT", contract.contractNumber, sourceSystem,
                externalCode);
    }

    /**
     * Whether a string is null or blank.
     *
     * @param value the string to test
     * @return {@code true} if null or blank
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
