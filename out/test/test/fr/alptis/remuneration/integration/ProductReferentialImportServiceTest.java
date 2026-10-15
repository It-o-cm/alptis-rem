package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.Insurer;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.ProductRange;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link ProductReferentialImportService}: insurers, ranges, products and options.
 */
@QuarkusTest
class ProductReferentialImportServiceTest {

    @Inject
    ProductReferentialImportService service;

    /**
     * Imports insurers, a range, a product and an option, in order.
     */
    @Test
    @TestTransaction
    void imports_the_product_referential() {
        ImportBatch insurers = service.importInsurers("ALPTIS_ASSURANCES", "REF", "ins.csv",
                "insurer_code;name;role\nUMR;Union Mutualiste Retraite;RISK_CARRIER\nBAD;Sans rôle;\n");
        assertEquals(1, insurers.linesLoaded);
        assertEquals(1, insurers.linesRejected);
        assertEquals("Union Mutualiste Retraite", Insurer.<Insurer>find("code", "UMR").firstResult().name);

        ImportBatch ranges = service.importRanges("ALPTIS_ASSURANCES", "REF", "rng.csv",
                "range_code;label;target\nSANTE_IND;Santé individuelle;INDIVIDUAL\n");
        assertEquals(1, ranges.linesLoaded);
        assertNotNull(ProductRange.find("code", "SANTE_IND").firstResult());

        ImportBatch products = service.importProducts("ALPTIS_ASSURANCES", "REF", "prd.csv",
                "entity_code;product_code;label;range_code;insurer_code;commissionable\n"
                        + "ALPTIS_ASSURANCES;SANTE_SELECT;Santé Select;SANTE_IND;UMR;true\n");
        assertEquals(1, products.linesLoaded);
        Product product = Product.find("code", "SANTE_SELECT").firstResult();
        assertEquals("SANTE_IND", product.range.code);

        ImportBatch options = service.importOptions("ALPTIS_ASSURANCES", "REF", "opt.csv",
                "product_code;option_code;label\nSANTE_SELECT;NIV2;Niveau 2\nUNKNOWN;X;orphan\n");
        assertEquals(1, options.linesLoaded);
        assertEquals(1, options.linesOnHold);
        assertNotNull(ProductOption.find("code", "NIV2").firstResult());
    }

    /**
     * A product line missing its label is rejected.
     */
    @Test
    @TestTransaction
    void rejects_a_product_missing_its_label() {
        ImportBatch batch = service.importProducts("ALPTIS_ASSURANCES", "REF", "prd.csv",
                "entity_code;product_code;label;range_code\nALPTIS_ASSURANCES;P;;R\n");
        assertEquals(1, batch.linesRejected);
    }

    /**
     * Exercises every validation leg, the type errors, the optional-field branches and the
     * create-or-update paths of all four product referentials.
     */
    @Test
    @TestTransaction
    void covers_all_branches() {
        // Insurers: new then updated (same code twice), missing code, missing name, bad role.
        ImportBatch insurers = service.importInsurers("ALPTIS_ASSURANCES", "REF", "ins.csv",
                "insurer_code;name;role\n"
                        + "UMR;Union;RISK_CARRIER\n"
                        + "UMR;Union MAJ;RISK_CARRIER\n"
                        + ";Nom;RISK_CARRIER\n"
                        + "X;;RISK_CARRIER\n"
                        + "Y;Nom;BOGUS\n");
        assertEquals(2, insurers.linesLoaded);
        assertEquals(3, insurers.linesRejected);
        assertEquals("Union MAJ", Insurer.<Insurer>find("code", "UMR").firstResult().name);

        // Ranges: set target then empty target (update), missing code, missing label, bad target.
        ImportBatch ranges = service.importRanges("ALPTIS_ASSURANCES", "REF", "rng.csv",
                "range_code;label;target\n"
                        + "R1;Label;INDIVIDUAL\n"
                        + "R1;Label2;\n"
                        + ";L;INDIVIDUAL\n"
                        + "R2;;INDIVIDUAL\n"
                        + "R3;L;BOGUS\n");
        assertEquals(2, ranges.linesLoaded);
        assertEquals(3, ranges.linesRejected);

        // Products: with insurer then without (update), missing code, missing range.
        ImportBatch products = service.importProducts("ALPTIS_ASSURANCES", "REF", "prd.csv",
                "entity_code;product_code;label;range_code;insurer_code;commissionable\n"
                        + "ALPTIS_ASSURANCES;P1;Prod;R1;UMR;false\n"
                        + "ALPTIS_ASSURANCES;P1;Prod MAJ;R1;;\n"
                        + "ALPTIS_ASSURANCES;;L;R1;;\n"
                        + "ALPTIS_ASSURANCES;P3;L;;;\n");
        assertEquals(2, products.linesLoaded);
        assertEquals(2, products.linesRejected);
        Product p1 = Product.find("code", "P1").firstResult();
        assertNull(p1.insurer);
        assertEquals(Boolean.TRUE, p1.commissionable);

        // Options: new then updated, missing product code, missing option code.
        ImportBatch options = service.importOptions("ALPTIS_ASSURANCES", "REF", "opt.csv",
                "product_code;option_code;label\n"
                        + "P1;O1;Opt\n"
                        + "P1;O1;Opt MAJ\n"
                        + ";O2;L\n"
                        + "P1;;L\n");
        assertEquals(2, options.linesLoaded);
        assertEquals(2, options.linesRejected);
        assertEquals("Opt MAJ", ProductOption.<ProductOption>find("code", "O1").firstResult().label);

        // A file whose header omits the label column: the absent column reads as empty and the line
        // is rejected for a missing field.
        ImportBatch noLabel = service.importOptions("ALPTIS_ASSURANCES", "REF", "opt-nolabel.csv",
                "product_code;option_code\nP1;O9\n");
        assertEquals(1, noLabel.linesRejected);
    }
}
