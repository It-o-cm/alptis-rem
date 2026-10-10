package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionPlan;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductionCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable in-memory index of the referential objects the demonstration dataset builds, shared between
 * the demo builders so later blocks (contracts, events, cycle) can reference the distributors,
 * products, production codes and plans created by the referential block without re-querying by code.
 *
 * <p>It is a plain holder: every lookup key is a business code, so the builders stay readable and the
 * dataset stays coherent across blocks.</p>
 */
public class DemoWorld {

    /** The legal entities by code. */
    final Map<String, LegalEntity> entities = new LinkedHashMap<>();

    /** The distributors by partner code. */
    final Map<String, Distributor> distributors = new LinkedHashMap<>();

    /** The production codes by {@code entityCode/code}. */
    final Map<String, ProductionCode> productionCodes = new LinkedHashMap<>();

    /** The products by {@code entityCode/code}. */
    final Map<String, Product> products = new LinkedHashMap<>();

    /** The commission plans by {@code entityCode/code}. */
    final Map<String, CommissionPlan> plans = new LinkedHashMap<>();

    /** The family-scene coverages by {@code entityCode/FAMILY}, used by the cycle runner to emit events. */
    final Map<String, Coverage> sceneCoverages = new LinkedHashMap<>();

    /**
     * Returns the key of an entity-scoped object.
     *
     * @param entityCode the entity code
     * @param code       the object's business code
     * @return the composite key
     */
    static String key(String entityCode, String code) {
        return entityCode + "/" + code;
    }

    /**
     * Records a production code under its entity/code key.
     *
     * @param pc the production code to index
     * @return the indexed production code
     */
    ProductionCode put(ProductionCode pc) {
        productionCodes.put(key(pc.entity.code, pc.code), pc);
        return pc;
    }

    /**
     * Records a product under its entity/code key.
     *
     * @param product the product to index
     * @return the indexed product
     */
    Product put(Product product) {
        products.put(key(product.entity.code, product.code), product);
        return product;
    }

    /**
     * Records a plan under its entity/code key.
     *
     * @param plan the plan to index
     * @return the indexed plan
     */
    CommissionPlan put(CommissionPlan plan) {
        plans.put(key(plan.entity.code, plan.code), plan);
        return plan;
    }

    /**
     * Records a distributor under its partner code.
     *
     * @param distributor the distributor to index
     * @return the indexed distributor
     */
    Distributor put(Distributor distributor) {
        distributors.put(distributor.partnerCode, distributor);
        return distributor;
    }

    /**
     * Returns the entity of the given code.
     *
     * @param code the entity code
     * @return the entity
     */
    LegalEntity entity(String code) {
        return entities.get(code);
    }

    /**
     * Returns the product of the given entity and code.
     *
     * @param entityCode the entity code
     * @param code       the product code
     * @return the product
     */
    Product product(String entityCode, String code) {
        return products.get(key(entityCode, code));
    }

    /**
     * Returns the production code of the given entity and code.
     *
     * @param entityCode the entity code
     * @param code       the production code
     * @return the production code
     */
    ProductionCode productionCode(String entityCode, String code) {
        return productionCodes.get(key(entityCode, code));
    }

    /**
     * Returns the plan of the given entity and code.
     *
     * @param entityCode the entity code
     * @param code       the plan code
     * @return the plan
     */
    CommissionPlan plan(String entityCode, String code) {
        return plans.get(key(entityCode, code));
    }

    /**
     * Returns the distributor of the given partner code.
     *
     * @param partnerCode the partner code
     * @return the distributor
     */
    Distributor distributor(String partnerCode) {
        return distributors.get(partnerCode);
    }

    /**
     * Records a family-scene coverage under {@code entityCode/family}.
     *
     * @param entityCode the entity code
     * @param family     the family tag
     * @param coverage   the coverage
     */
    void putScene(String entityCode, String family, Coverage coverage) {
        sceneCoverages.put(key(entityCode, family), coverage);
    }

    /**
     * Returns the family-scene coverage of the given entity and family tag.
     *
     * @param entityCode the entity code
     * @param family     the family tag
     * @return the coverage, or null if none
     */
    Coverage scene(String entityCode, String family) {
        return sceneCoverages.get(key(entityCode, family));
    }
}
