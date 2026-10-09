package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ImportBatch;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the sales-force referential import (FDV-01/02, IN-09): collaborators and measures, with
 * manager resolution, hold on an unknown reference and rejection of malformed lines. Routed through
 * {@link ImportService} so the flow dispatch is exercised; runs in rolled-back transactions.
 */
@QuarkusTest
class EmployeeImportServiceTest {

    @Inject
    ImportService imports;

    /**
     * The collaborators import loads the valid rows, resolves the manager, holds an unknown manager
     * and rejects the bad job code and the missing field.
     */
    @Test
    @TestTransaction
    void imports_collaborators() {
        String csv = "entity_code;external_id;last_name;first_name;job_code;team;region;manager_external_id;valid_from;valid_to\n"
                + "UNEP;M1;Manager;Anne;DR;;;;2026-01-01;2026-12-31\n"
                + "UNEP;E1;Dupont;Jean;CAC;Equipe1;Sud;M1;2026-01-01;\n"
                + "UNEP;E2;Martin;Luc;CAC;;;GHOST;2026-01-01;\n"
                + "UNEP;;No;Id;CAC;;;;2026-01-01;\n"
                + "UNEP;E4;;Jean;CAC;;;;2026-01-01;\n"
                + "UNEP;E5;Nom;;CAC;;;;2026-01-01;\n"
                + "UNEP;E6;Nom;Jean;;;;;2026-01-01;\n"
                + "UNEP;E7;Nom;Jean;CAC;;;;;\n"
                + "UNEP;E8;Bad;Job;BADJOB;;;;2026-01-01;\n"
                + "UNEP;E9;Bad;Date;CAC;;;;NOTADATE;\n";
        ImportBatch batch = imports.importFile("UNEP", EmployeeImportService.EMPLOYEES, "SYS", "employees.csv",
                csv);
        assertEquals(2, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(7, batch.linesRejected);
    }

    /**
     * The measures import loads the valid measure, holds the unknown collaborator and rejects the bad
     * type, the bad value and the missing period.
     */
    @Test
    @TestTransaction
    void imports_measures() {
        imports.importFile("UNEP", EmployeeImportService.EMPLOYEES, "SYS", "employees.csv",
                "entity_code;external_id;last_name;first_name;job_code;valid_from\nUNEP;E1;Dupont;Jean;CAC;2026-01-01\n");
        String csv = "external_id;measure_type;dimension;period_from;period_to;value\n"
                + "E1;PRODUCTION;particulier;2026-09-01;2026-09-30;10000.00\n"
                + "GHOST;PRODUCTION;particulier;2026-09-01;2026-09-30;100\n"
                + ";PRODUCTION;x;2026-09-01;2026-09-30;5\n"
                + "E1;;x;2026-09-01;2026-09-30;5\n"
                + "E1;ABSENCE;x;;2026-09-30;5\n"
                + "E1;ABSENCE;x;2026-09-01;;5\n"
                + "E1;ABSENCE;x;2026-09-01;2026-09-30;\n"
                + "E1;BADTYPE;x;2026-09-01;2026-09-30;100\n"
                + "E1;ABSENCE;x;NOTADATE;2026-09-30;5\n"
                + "E1;ABSENCE;x;2026-09-01;NOTADATE;5\n"
                + "E1;ABSENCE;conges;2026-09-01;2026-09-30;abc\n";
        ImportBatch batch = imports.importFile("UNEP", EmployeeImportService.EMPLOYEE_MEASURES, "SYS",
                "measures.csv", csv);
        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        assertEquals(9, batch.linesRejected);
    }
}
