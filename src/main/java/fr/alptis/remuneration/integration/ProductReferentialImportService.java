package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.Insurer;
import fr.alptis.remuneration.domain.InsurerRole;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.ProductRange;
import fr.alptis.remuneration.domain.ProductTarget;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Map;

/**
 * Imports the product referentials of IN-07 (section 9.10): insurers, product ranges, products and
 * product options. Each file is journalled and idempotent by fingerprint.
 */
@ApplicationScoped
public class ProductReferentialImportService {

    /** Insurers file flow code. */
    public static final String INSURERS = "insurers";

    /** Product ranges file flow code. */
    public static final String PRODUCT_RANGES = "product_ranges";

    /** Products file flow code. */
    public static final String PRODUCTS = "products";

    /** Product options file flow code. */
    public static final String PRODUCT_OPTIONS = "product_options";

    /** The date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public ProductReferentialImportService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Imports an insurers file.
     *
     * @param entityCode   the entity code (routing only; insurers are shared)
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importInsurers(String entityCode, String sourceSystem, String fileName, String content) {
        return ImportJournal.run(clock, entityCode, INSURERS, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "insurer_code").isEmpty() || value(row, "name").isEmpty()
                            || value(row, "role").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    return parseRole(value(row, "role")) == null ? RejectionReasonCode.TYPE : null;
                },
                row -> {
                    Insurer insurer = Insurer.find("code", value(row, "insurer_code")).firstResult();
                    if (insurer == null) {
                        insurer = new Insurer();
                        insurer.code = value(row, "insurer_code");
                        insurer.persist();
                    }
                    insurer.name = value(row, "name");
                    insurer.role = parseRole(value(row, "role"));
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports a product-ranges file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importRanges(String entityCode, String sourceSystem, String fileName, String content) {
        return ImportJournal.run(clock, entityCode, PRODUCT_RANGES, sourceSystem, fileName, content,
                row -> {
                    if (value(row, "range_code").isEmpty() || value(row, "label").isEmpty()) {
                        return RejectionReasonCode.MISSING_FIELD;
                    }
                    return parseTarget(value(row, "target")) == null && !value(row, "target").isEmpty()
                            ? RejectionReasonCode.TYPE : null;
                },
                row -> {
                    ProductRange range = ProductRange.find("code", value(row, "range_code")).firstResult();
                    if (range == null) {
                        range = new ProductRange();
                        range.code = value(row, "range_code");
                        range.persist();
                    }
                    range.label = value(row, "label");
                    range.target = parseTarget(value(row, "target"));
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports a products file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importProducts(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, PRODUCTS, sourceSystem, fileName, content,
                row -> (value(row, "product_code").isEmpty() || value(row, "label").isEmpty()
                        || value(row, "range_code").isEmpty()) ? RejectionReasonCode.MISSING_FIELD : null,
                row -> {
                    Product product = Product.find("entity = ?1 and code = ?2", entity,
                            value(row, "product_code")).firstResult();
                    if (product == null) {
                        product = new Product();
                        product.entity = entity;
                        product.code = value(row, "product_code");
                        product.persist();
                    }
                    product.label = value(row, "label");
                    product.range = ProductRange.find("code", value(row, "range_code")).firstResult();
                    product.insurer = Insurer.find("code", value(row, "insurer_code")).firstResult();
                    product.commissionable = !"false".equals(value(row, "commissionable"));
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Imports a product-options file.
     *
     * @param entityCode   the entity code
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     */
    @Transactional
    public ImportBatch importOptions(String entityCode, String sourceSystem, String fileName, String content) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        return ImportJournal.run(clock, entityCode, PRODUCT_OPTIONS, sourceSystem, fileName, content,
                row -> (value(row, "product_code").isEmpty() || value(row, "option_code").isEmpty()
                        || value(row, "label").isEmpty()) ? RejectionReasonCode.MISSING_FIELD : null,
                row -> {
                    Product product = Product.find("entity = ?1 and code = ?2", entity,
                            value(row, "product_code")).firstResult();
                    if (product == null) {
                        return ImportJournal.LineResult.ON_HOLD;
                    }
                    ProductOption option = ProductOption.find("product = ?1 and code = ?2", product,
                            value(row, "option_code")).firstResult();
                    if (option == null) {
                        option = new ProductOption();
                        option.product = product;
                        option.code = value(row, "option_code");
                        option.persist();
                    }
                    option.label = value(row, "label");
                    return ImportJournal.LineResult.LOADED;
                });
    }

    /**
     * Parses an insurer role, returning {@code null} when unrecognised.
     *
     * @param raw the raw value
     * @return the role, or {@code null}
     */
    private static InsurerRole parseRole(String raw) {
        try {
            return InsurerRole.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a product target, returning {@code null} when empty or unrecognised.
     *
     * @param raw the raw value
     * @return the target, or {@code null}
     */
    private static ProductTarget parseTarget(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return ProductTarget.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the value of a column, or an empty string when absent.
     *
     * @param row    the parsed row
     * @param column the column name
     * @return the value, never null
     */
    private static String value(Map<String, String> row, String column) {
        String raw = row.get(column);
        return raw == null ? "" : raw;
    }
}
