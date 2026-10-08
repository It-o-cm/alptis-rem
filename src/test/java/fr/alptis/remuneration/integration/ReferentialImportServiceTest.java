package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorEntity;
import fr.alptis.remuneration.domain.DistributorGroup;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ProductionCode;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link ReferentialImportService}: distributors with their per-entity status, groups and
 * production codes, with the five-counter journal and the reject codes.
 */
@QuarkusTest
class ReferentialImportServiceTest {

    @Inject
    ReferentialImportService service;

    /**
     * Imports distributors: a valid line is loaded, a missing code and a bad type are rejected.
     */
    @Test
    @TestTransaction
    void imports_distributors_with_status() {
        String content = "entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n"
                + "ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n"
                + "ALPTIS_ASSURANCES;SESAME;;Sans code;INDEPENDENT;ACTIVE;2026-01-01\n"
                + "ALPTIS_ASSURANCES;SESAME;P2;Cabinet X;BOGUS;ACTIVE;2026-01-01\n";

        ImportBatch batch = service.importDistributors("ALPTIS_ASSURANCES", "SESAME", "dist.csv", content);

        assertEquals(3, batch.linesRead);
        assertEquals(1, batch.linesLoaded);
        assertEquals(2, batch.linesRejected);
        Distributor distributor = Distributor.find("partnerCode", "P13897").firstResult();
        assertNotNull(distributor);
        DistributorEntity link = DistributorEntity.find("distributor", distributor).firstResult();
        assertEquals("ACTIVE", link.status.name());
    }

    /**
     * Imports a group and a production code linked to a distributor.
     */
    @Test
    @TestTransaction
    void imports_groups_and_production_codes() {
        service.importDistributors("ALPTIS_ASSURANCES", "SESAME", "dist.csv",
                "entity_code;source_system;partner_code;legal_name;distributor_type;status;valid_from\n"
                        + "ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n");

        ImportBatch groups = service.importGroups("ALPTIS_ASSURANCES", "SESAME", "grp.csv",
                "group_code;label;head_partner_code\nG1;Réseau Martin;P13897\n");
        assertEquals(1, groups.linesLoaded);
        DistributorGroup group = DistributorGroup.find("code", "G1").firstResult();
        assertEquals("Réseau Martin", group.label);
        assertEquals("P13897", group.headDistributor.partnerCode);

        ImportBatch codes = service.importProductionCodes("ALPTIS_ASSURANCES", "SESAME", "pc.csv",
                "entity_code;production_code;partner_code;label;status\n"
                        + "ALPTIS_ASSURANCES;61723;P13897;Agence Lyon;ACTIVE\n");
        assertEquals(1, codes.linesLoaded);
        ProductionCode pc = ProductionCode.find("code", "61723").firstResult();
        assertEquals("P13897", pc.distributor.partnerCode);
    }

    /**
     * Exercises every validation leg and the create-or-update paths: each missing-field leg, each
     * type error, the optional-field branches (group link, commissionable flag, empty label) and the
     * re-import of an existing distributor, group and production code.
     */
    @Test
    @TestTransaction
    void covers_all_branches() {
        // Groups: one with an empty head (no lookup), one with a head (lookup, still absent),
        // a missing code and a missing label; then a re-import updating the existing group.
        ImportBatch groups = service.importGroups("ALPTIS_ASSURANCES", "SESAME", "g1.csv",
                "group_code;label;head_partner_code\n"
                        + "G1;Réseau;\n"
                        + "G2;Autre;P13897\n"
                        + ";Sans code;\n"
                        + "G3;;\n");
        assertEquals(2, groups.linesLoaded);
        assertEquals(2, groups.linesRejected);
        service.importGroups("ALPTIS_ASSURANCES", "SESAME", "g2.csv",
                "group_code;label;head_partner_code\nG1;Réseau 2;\n");
        assertEquals("Réseau 2", DistributorGroup.<DistributorGroup>find("code", "G1").firstResult().label);

        // Distributors: a full valid line (group link, commissionable=false, advance excluded,
        // valid_to), then one missing each required field, a bad status and a bad date.
        String header = "entity_code;source_system;partner_code;legal_name;distributor_type;status;"
                + "valid_from;valid_to;group_code;commissionable;advance_excluded\n";
        ImportBatch dist = service.importDistributors("ALPTIS_ASSURANCES", "SESAME", "d1.csv",
                header
                        + "ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01;2026-12-31;G1;false;true\n"
                        + "ALPTIS_ASSURANCES;SESAME;P2;;INDEPENDENT;ACTIVE;2026-01-01\n"
                        + "ALPTIS_ASSURANCES;SESAME;P3;Nom;INDEPENDENT;;2026-01-01\n"
                        + "ALPTIS_ASSURANCES;SESAME;P4;Nom;;ACTIVE;2026-01-01\n"
                        + "ALPTIS_ASSURANCES;SESAME;P5;Nom;INDEPENDENT;ACTIVE;\n"
                        + "ALPTIS_ASSURANCES;SESAME;P6;Nom;INDEPENDENT;BOGUS;2026-01-01\n"
                        + "ALPTIS_ASSURANCES;SESAME;P7;Nom;INDEPENDENT;ACTIVE;notadate\n");
        assertEquals(1, dist.linesLoaded);
        assertEquals(6, dist.linesRejected);
        Distributor p = Distributor.find("partnerCode", "P13897").firstResult();
        assertEquals("G1", p.group.code);
        assertEquals(Boolean.FALSE, p.commissionable);
        assertEquals(Boolean.TRUE, p.advanceExcluded);

        // Re-import the same distributor: existing distributor and existing link, no group,
        // commissionable left to its default (true).
        service.importDistributors("ALPTIS_ASSURANCES", "SESAME", "d2.csv",
                header + "ALPTIS_ASSURANCES;SESAME;P13897;Cabinet Martin;INDEPENDENT;ACTIVE;2026-01-01\n");
        p = Distributor.find("partnerCode", "P13897").firstResult();
        assertNull(p.group);
        assertEquals(Boolean.TRUE, p.commissionable);
        assertEquals(1, DistributorEntity.count("distributor", p));

        // Production codes: a line with a label, one without, and the four reject legs.
        ImportBatch codes = service.importProductionCodes("ALPTIS_ASSURANCES", "SESAME", "pc1.csv",
                "entity_code;production_code;partner_code;label;status\n"
                        + "ALPTIS_ASSURANCES;61723;P13897;Agence Lyon;ACTIVE\n"
                        + "ALPTIS_ASSURANCES;66117;P13897;;ACTIVE\n"
                        + "ALPTIS_ASSURANCES;;P13897;X;ACTIVE\n"
                        + "ALPTIS_ASSURANCES;61999;;X;ACTIVE\n"
                        + "ALPTIS_ASSURANCES;61888;P13897;X;\n"
                        + "ALPTIS_ASSURANCES;61777;P13897;X;BOGUS\n");
        assertEquals(2, codes.linesLoaded);
        assertEquals(4, codes.linesRejected);
        assertNull(ProductionCode.<ProductionCode>find("code", "66117").firstResult().label);

        // Re-import an existing production code (update path).
        service.importProductionCodes("ALPTIS_ASSURANCES", "SESAME", "pc2.csv",
                "entity_code;production_code;partner_code;label;status\n"
                        + "ALPTIS_ASSURANCES;61723;P13897;Agence Lyon 2;ACTIVE\n");
        assertEquals("Agence Lyon 2", ProductionCode.<ProductionCode>find("code", "61723").firstResult().label);
    }
}
