package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.Insurer;
import fr.alptis.remuneration.domain.InsurerRole;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.OptionType;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ProductTarget;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.ValidationException;
import fr.alptis.remuneration.security.AuditService;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;

/**
 * Manages the product referential with validity dates (REF-01, INT-24): creates insurers, ranges,
 * options and products, each with a validity window, and ends a validity. Two validity windows of the
 * same object must not overlap (section 5.7); an overlapping creation is refused. Changes are audited.
 */
@ApplicationScoped
public class ReferentialAdminService {

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
    public ReferentialAdminService(EntityScope scope, CurrentUser currentUser, AuditService audit) {
        this.scope = scope;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /**
     * Creates an insurer with a validity window (REF-01).
     *
     * @param code      the insurer code
     * @param name      the insurer name
     * @param role      the role (RISK_CARRIER, DELEGATED_MANAGER), or null
     * @param validFrom the first day of validity
     * @param validTo   the last day of validity, or null while in force
     * @throws ValidationException if a field is invalid or the window overlaps the existing insurer
     */
    @Transactional
    public void createInsurer(String code, String name, String role, LocalDate validFrom, LocalDate validTo) {
        requireCode(code);
        requireWindow(validFrom, validTo);
        if (Insurer.count("code", code) > 0) {
            throw overlap(code);
        }
        Insurer insurer = new Insurer();
        insurer.code = code;
        insurer.name = name;
        insurer.role = parseRole(role);
        insurer.validFrom = validFrom;
        insurer.validTo = validTo;
        insurer.persist();
        audit.record(currentUser.username(), null, "CREATE_INSURER", "Insurer", insurer.getId(), null, quote(code));
    }

    /**
     * Creates a product range with a validity window (REF-01).
     *
     * @param code      the range code
     * @param label     the range label
     * @param target    the target clientele (INDIVIDUAL, PROFESSIONAL, COMPANY), or null
     * @param validFrom the first day of validity
     * @param validTo   the last day of validity, or null while in force
     * @throws ValidationException if a field is invalid or the window overlaps the existing range
     */
    @Transactional
    public void createRange(String code, String label, String target, LocalDate validFrom, LocalDate validTo) {
        requireCode(code);
        requireWindow(validFrom, validTo);
        if (ProductRange.count("code", code) > 0) {
            throw overlap(code);
        }
        ProductRange range = new ProductRange();
        range.code = code;
        range.label = label;
        range.target = parseTarget(target);
        range.validFrom = validFrom;
        range.validTo = validTo;
        range.persist();
        audit.record(currentUser.username(), null, "CREATE_RANGE", "ProductRange", range.getId(), null, quote(code));
    }

    /**
     * Creates an option of a product with a validity window (REF-01).
     *
     * @param productCode the code of the product the option belongs to
     * @param code        the option code
     * @param label       the option label
     * @param optionType  the option type (BASE, RIDER, PACK, FUND), or null
     * @param validFrom   the first day of validity
     * @param validTo     the last day of validity, or null while in force
     * @throws ResourceNotFoundException if the product is unknown or out of scope
     * @throws ValidationException       if a field is invalid or the window overlaps the existing option
     */
    @Transactional
    public void createOption(String productCode, String code, String label, String optionType, LocalDate validFrom,
            LocalDate validTo) {
        requireCode(code);
        requireWindow(validFrom, validTo);
        Product product = Product.<Product>find("code", productCode).firstResult();
        if (product == null || !scope.canSee(product.entity.code)) {
            throw new ResourceNotFoundException("Produit introuvable : " + productCode);
        }
        if (ProductOption.count("product = ?1 and code = ?2", product, code) > 0) {
            throw overlap(code);
        }
        ProductOption option = new ProductOption();
        option.product = product;
        option.code = code;
        option.label = label;
        option.optionType = parseOptionType(optionType);
        option.validFrom = validFrom;
        option.validTo = validTo;
        option.persist();
        audit.record(currentUser.username(), product.entity, "CREATE_OPTION", "ProductOption", option.getId(), null,
                quote(code));
    }

    /**
     * Creates a product with a marketing (validity) window (REF-01).
     *
     * @param entityCode  the entity the product belongs to
     * @param code        the product code
     * @param label       the product label
     * @param rangeCode   the range code, or null
     * @param insurerCode the insurer code, or null
     * @param validFrom   the first day of marketing
     * @param validTo     the last day of marketing, or null while marketed
     * @throws ResourceNotFoundException if the entity is out of scope
     * @throws ValidationException       if a field is invalid or the window overlaps the existing product
     */
    @Transactional
    public void createProduct(String entityCode, String code, String label, String rangeCode, String insurerCode,
            LocalDate validFrom, LocalDate validTo) {
        requireCode(code);
        requireWindow(validFrom, validTo);
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null || !scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        if (Product.count("entity = ?1 and code = ?2", entity, code) > 0) {
            throw overlap(code);
        }
        Product product = new Product();
        product.entity = entity;
        product.code = code;
        product.label = label;
        product.range = rangeCode == null ? null : ProductRange.find("code", rangeCode).firstResult();
        product.insurer = insurerCode == null ? null : Insurer.find("code", insurerCode).firstResult();
        product.commissionable = Boolean.TRUE;
        product.marketedFrom = validFrom;
        product.marketedTo = validTo;
        product.persist();
        audit.record(currentUser.username(), entity, "CREATE_PRODUCT", "Product", product.getId(), null, quote(code));
    }

    /**
     * Ends the validity of a referential object by setting its last valid day (REF-01).
     *
     * @param objectType  the object kind: insurer, range, option or product
     * @param code        the object code
     * @param productCode the owning product code, required for an option
     * @param entityCode  the owning entity code, required for a product
     * @param validTo     the last day of validity
     * @throws ResourceNotFoundException if the object is unknown or out of scope
     * @throws ValidationException       if the type is unknown or the end date is invalid
     */
    @Transactional
    public void endValidity(String objectType, String code, String productCode, String entityCode, LocalDate validTo) {
        if (validTo == null) {
            throw new ValidationException("La date de fin de validité est exigée");
        }
        if ("insurer".equals(objectType)) {
            Insurer insurer = required(Insurer.find("code", code).firstResult(), code);
            requireWindow(insurer.validFrom, validTo);
            insurer.validTo = validTo;
            audit.record(currentUser.username(), null, "END_INSURER", "Insurer", insurer.getId(), null, quote(code));
        } else if ("range".equals(objectType)) {
            ProductRange range = required(ProductRange.find("code", code).firstResult(), code);
            requireWindow(range.validFrom, validTo);
            range.validTo = validTo;
            audit.record(currentUser.username(), null, "END_RANGE", "ProductRange", range.getId(), null, quote(code));
        } else if ("option".equals(objectType)) {
            Product product = Product.<Product>find("code", productCode).firstResult();
            if (product == null || !scope.canSee(product.entity.code)) {
                throw new ResourceNotFoundException("Produit introuvable : " + productCode);
            }
            ProductOption option = required(
                    ProductOption.find("product = ?1 and code = ?2", product, code).firstResult(), code);
            requireWindow(option.validFrom, validTo);
            option.validTo = validTo;
            audit.record(currentUser.username(), product.entity, "END_OPTION", "ProductOption", option.getId(),
                    null, quote(code));
        } else if ("product".equals(objectType)) {
            LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
            if (entity == null || !scope.canSee(entityCode)) {
                throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
            }
            Product product = required(Product.find("entity = ?1 and code = ?2", entity, code).firstResult(), code);
            requireWindow(product.marketedFrom, validTo);
            product.marketedTo = validTo;
            audit.record(currentUser.username(), entity, "END_PRODUCT", "Product", product.getId(), null, quote(code));
        } else {
            throw new ValidationException("Type d'objet inconnu : " + objectType);
        }
    }

    /**
     * Validates that a code is present.
     *
     * @param code the code to check
     * @throws ValidationException if the code is null or blank
     */
    private static void requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("Le code est exigé");
        }
    }

    /**
     * Validates a validity window: a start is required, and an end cannot precede it.
     *
     * @param validFrom the window start
     * @param validTo   the window end, or null
     * @throws ValidationException if the start is missing or the end precedes the start
     */
    private static void requireWindow(LocalDate validFrom, LocalDate validTo) {
        if (validFrom == null) {
            throw new ValidationException("La date de début de validité est exigée");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new ValidationException("La date de fin précède la date de début");
        }
    }

    /**
     * Returns the object or raises a not-found error.
     *
     * @param object the object, possibly null
     * @param code   the code searched, for the message
     * @param <T>    the object type
     * @return the object
     * @throws ResourceNotFoundException if the object is null
     */
    private static <T> T required(T object, String code) {
        if (object == null) {
            throw new ResourceNotFoundException("Objet introuvable : " + code);
        }
        return object;
    }

    /**
     * Wraps a code as a JSON string literal for the audit trail's JSON {@code after} value.
     *
     * @param code the business code
     * @return the code as a JSON string
     */
    private static String quote(String code) {
        return "\"" + code + "\"";
    }

    /**
     * Builds the overlapping-validity error for a code.
     *
     * @param code the object code
     * @return the validation exception to throw
     */
    private static ValidationException overlap(String code) {
        return new ValidationException("Chevauchement de validité pour " + code);
    }

    /**
     * Parses an insurer role, treating blank as absent.
     *
     * @param role the raw role, or null
     * @return the role, or null
     * @throws ValidationException if the value is not a role
     */
    private static InsurerRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return InsurerRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Rôle d'assureur inconnu : " + role);
        }
    }

    /**
     * Parses a product target, treating blank as absent.
     *
     * @param target the raw target, or null
     * @return the target, or null
     * @throws ValidationException if the value is not a target
     */
    private static ProductTarget parseTarget(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        try {
            return ProductTarget.valueOf(target);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Cible de gamme inconnue : " + target);
        }
    }

    /**
     * Parses an option type, treating blank as absent.
     *
     * @param optionType the raw option type, or null
     * @return the option type, or null
     * @throws ValidationException if the value is not an option type
     */
    private static OptionType parseOptionType(String optionType) {
        if (optionType == null || optionType.isBlank()) {
            return null;
        }
        try {
            return OptionType.valueOf(optionType);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Type d'option inconnu : " + optionType);
        }
    }
}
