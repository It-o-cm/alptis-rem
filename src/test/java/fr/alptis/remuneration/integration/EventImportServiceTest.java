package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.ConflictException;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.HoldReason;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ImportBatchStatus;
import fr.alptis.remuneration.domain.ImportRejection;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.RejectionReasonCode;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link EventImportService}: the controls and reject codes of CR-67, the reimport refusal of
 * CR-68, idempotency by source key and the on-hold path for unknown references.
 */
@QuarkusTest
class EventImportServiceTest {

    private static final String HEADER =
            "entity_code;source_system;source_key;event_type;contract_number;coverage_code;event_date;gross_amount";

    @Inject
    EventImportService service;

    /** Creates the Alptis contract AD-1 with coverage C1 used by the rows. */
    private void seedContract() {
        LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
        Contract contract = new Contract();
        contract.entity = alptis;
        contract.sourceSystem = "CLEVA";
        contract.contractNumber = "AD-1";
        contract.persist();
        Coverage coverage = new Coverage();
        coverage.contract = contract;
        coverage.coverageCode = "C1";
        coverage.effectDate = LocalDate.of(2020, 1, 1);
        coverage.persist();
    }

    private static String row(String key, String type, String contract, String coverage, String date, String gross) {
        return "ALPTIS_ASSURANCES;CLEVA;" + key + ";" + type + ";" + contract + ";" + coverage + ";" + date + ";" + gross;
    }

    /**
     * CR-67: a null premium, a missing contract number and a future date are rejected with their
     * codes; an unknown reference is put on hold; the rest is integrated.
     */
    @Test
    @TestTransaction
    void cr67_bad_lines_are_rejected_or_held_the_rest_loaded() {
        seedContract();
        String content = HEADER + "\n"
                + row("ENC-1", "PREMIUM_COLLECTED", "AD-1", "C1", "2020-01-05", "100.00") + "\n"
                + row("ENC-2", "PREMIUM_COLLECTED", "AD-1", "C1", "2020-01-05", "0.00") + "\n"
                + row("ENC-3", "PREMIUM_COLLECTED", "", "C1", "2020-01-05", "100.00") + "\n"
                + row("ENC-4", "PREMIUM_COLLECTED", "AD-1", "C1", "2999-01-01", "100.00") + "\n"
                + row("ENC-5", "PREMIUM_COLLECTED", "AD-UNKNOWN", "C1", "2020-01-05", "100.00") + "\n";

        ImportBatch batch = service.importEvents("ALPTIS_ASSURANCES", "CLEVA", "events.csv", content);

        assertEquals(5, batch.linesRead);
        assertEquals(1, batch.linesLoaded);
        assertEquals(3, batch.linesRejected);
        assertEquals(1, batch.linesOnHold);
        assertEquals(0, batch.linesFiltered);
        assertEquals(ImportBatchStatus.PARTIALLY_LOADED, batch.status);

        List<ImportRejection> rejections = ImportRejection.list("importBatch", batch);
        List<RejectionReasonCode> codes = rejections.stream().map(r -> r.reasonCode).toList();
        assertTrue(codes.contains(RejectionReasonCode.AMOUNT));
        assertTrue(codes.contains(RejectionReasonCode.MISSING_FIELD));
        assertTrue(codes.contains(RejectionReasonCode.FUTURE_DATE));

        BusinessEvent loaded = BusinessEvent.find("sourceKey", "ENC-1").firstResult();
        assertEquals(ProcessingStatus.PENDING, loaded.processingStatus);
        BusinessEvent held = BusinessEvent.find("sourceKey", "ENC-5").firstResult();
        assertEquals(ProcessingStatus.ON_HOLD, held.processingStatus);
        assertEquals(HoldReason.UNKNOWN_REFERENCE, held.holdReason);
    }

    /**
     * A row carrying more fields than the header is rejected as a structural error (section 8.2), the
     * well-formed rows loading as usual.
     */
    @Test
    @TestTransaction
    void a_row_with_an_extra_field_is_a_structure_error() {
        seedContract();
        String content = HEADER + "\n"
                + row("ENC-1", "PREMIUM_COLLECTED", "AD-1", "C1", "2020-01-05", "100.00") + "\n"
                + row("ENC-2", "PREMIUM_COLLECTED", "AD-1", "C1", "2020-01-05", "100.00") + ";surplus\n";

        ImportBatch batch = service.importEvents("ALPTIS_ASSURANCES", "CLEVA", "struct.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesRejected);
        assertEquals(List.of(RejectionReasonCode.STRUCTURE), ImportRejection.<ImportRejection>list("importBatch", batch)
                .stream().map(r -> r.reasonCode).toList());
    }

    /**
     * CR-68: depositing the same file twice is refused and creates no duplicate event.
     */
    @Test
    @TestTransaction
    void cr68_reimport_is_refused_without_duplicate_events() {
        seedContract();
        String content = HEADER + "\n" + row("ENC-1", "PREMIUM_COLLECTED", "AD-1", "C1", "2020-01-05", "100.00") + "\n";

        service.importEvents("ALPTIS_ASSURANCES", "CLEVA", "events.csv", content);
        long after = BusinessEvent.count("sourceKey", "ENC-1");

        assertThrows(ConflictException.class,
                () -> service.importEvents("ALPTIS_ASSURANCES", "CLEVA", "events.csv", content));
        assertEquals(after, BusinessEvent.count("sourceKey", "ENC-1"));
    }

    /**
     * An unknown event type and an unparseable date are rejected as TYPE errors.
     */
    @Test
    @TestTransaction
    void unknown_type_or_bad_date_is_a_type_error() {
        seedContract();
        String content = HEADER + "\n"
                + row("E-1", "NOT_A_TYPE", "AD-1", "C1", "2020-01-05", "100.00") + "\n"
                + row("E-2", "PREMIUM_COLLECTED", "AD-1", "C1", "not-a-date", "100.00") + "\n";

        ImportBatch batch = service.importEvents("ALPTIS_ASSURANCES", "CLEVA", "ev2.csv", content);

        assertEquals(2, batch.linesRejected);
        List<RejectionReasonCode> codes = ImportRejection.<ImportRejection>list("importBatch", batch)
                .stream().map(r -> r.reasonCode).toList();
        assertEquals(List.of(RejectionReasonCode.TYPE, RejectionReasonCode.TYPE), codes);
    }

    /**
     * A source key already present is rejected as a duplicate.
     */
    @Test
    @TestTransaction
    void existing_source_key_is_a_duplicate() {
        seedContract();
        LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
        BusinessEvent existing = new BusinessEvent();
        existing.entity = alptis;
        existing.sourceSystem = "CLEVA";
        existing.sourceKey = "DUP-1";
        existing.eventType = EventType.PREMIUM_COLLECTED;
        existing.eventDate = LocalDate.of(2020, 1, 5);
        existing.processingStatus = ProcessingStatus.PENDING;
        existing.persist();

        String content = HEADER + "\n" + row("DUP-1", "PREMIUM_COLLECTED", "AD-1", "C1", "2020-01-05", "100.00") + "\n";
        ImportBatch batch = service.importEvents("ALPTIS_ASSURANCES", "CLEVA", "dup.csv", content);

        assertEquals(1, batch.linesRejected);
        assertEquals(RejectionReasonCode.DUPLICATE,
                ImportRejection.<ImportRejection>find("importBatch", batch).firstResult().reasonCode);
    }

    /**
     * A non-premium life event without an amount is loaded, as is a premium with an explicit basis
     * on a known coverage; a coverage code that is given but unknown is put on hold.
     */
    @Test
    @TestTransaction
    void loads_life_events_and_holds_unknown_coverage() {
        seedContract();
        String content = HEADER + "\n"
                + row("T-1", "TERMINATION", "AD-1", "C1", "2020-06-01", "") + "\n"
                + row("C-1", "PREMIUM_COLLECTED", "AD-1", "C-UNKNOWN", "2020-01-05", "100.00") + "\n";

        ImportBatch batch = service.importEvents("ALPTIS_ASSURANCES", "CLEVA", "mix.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(1, batch.linesOnHold);
        BusinessEvent termination = BusinessEvent.find("sourceKey", "T-1").firstResult();
        assertEquals(ProcessingStatus.PENDING, termination.processingStatus);
        assertNull(termination.grossAmount);
        BusinessEvent heldCoverage = BusinessEvent.find("sourceKey", "C-1").firstResult();
        assertEquals(HoldReason.UNKNOWN_REFERENCE, heldCoverage.holdReason);
    }

    /**
     * A loaded premium on a known coverage records the coverage and a computed basis.
     */
    @Test
    @TestTransaction
    void loads_premium_with_coverage_and_computed_basis() {
        seedContract();
        String content = HEADER + ";tax_amount\n"
                + row("P-1", "PREMIUM_COLLECTED", "AD-1", "C1", "2020-02-01", "113.27") + ";13.27\n";

        ImportBatch batch = service.importEvents("ALPTIS_ASSURANCES", "CLEVA", "basis.csv", content);

        assertEquals(1, batch.linesLoaded);
        BusinessEvent event = BusinessEvent.find("sourceKey", "P-1").firstResult();
        assertEquals("C1", event.coverage.coverageCode);
        assertEquals(0, event.basisAmount.compareTo(new java.math.BigDecimal("100.00")));
    }

    /**
     * Importing for an unknown entity is refused.
     */
    @Test
    @TestTransaction
    void unknown_entity_is_refused() {
        assertThrows(ConflictException.class,
                () -> service.importEvents("NOPE", "CLEVA", "x.csv", HEADER + "\n"));
    }

    /**
     * Covers the remaining single-row branches: a premium issued with an explicit basis and the
     * optional text fields set (with a malformed precomputed amount), a premium with no amount, and
     * the missing source-key and event-type legs.
     */
    @Test
    @TestTransaction
    void covers_single_row_branches() {
        seedContract();
        String header = HEADER + ";basis_amount;fund_code;management_profile;precomputed_basis;beneficiary_code;label\n";
        String content = header
                + "ALPTIS_ASSURANCES;CLEVA;I-1;PREMIUM_ISSUED;AD-1;C1;2020-02-01;200.00;150.00;F1;MP;xx;B1;Libellé\n"
                + "ALPTIS_ASSURANCES;CLEVA;N-1;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;\n"
                + "ALPTIS_ASSURANCES;CLEVA;;PREMIUM_COLLECTED;AD-1;C1;2020-01-05;100.00\n"
                + "ALPTIS_ASSURANCES;CLEVA;E-1;;AD-1;C1;2020-01-05;100.00\n";

        ImportBatch batch = service.importEvents("ALPTIS_ASSURANCES", "CLEVA", "branches.csv", content);

        assertEquals(1, batch.linesLoaded);
        assertEquals(3, batch.linesRejected);
        BusinessEvent issued = BusinessEvent.find("sourceKey", "I-1").firstResult();
        assertEquals(0, issued.basisAmount.compareTo(new java.math.BigDecimal("150.00")));
        assertEquals("F1", issued.fundCode);
        assertEquals("Libellé", issued.label);
        assertNull(issued.precomputedBasis);
    }

    /**
     * Imports pre-mapped groups: a group whose derived row is valid (persisted with the primary) and
     * a group whose derived row is rejected (skipped), keeping the counters at the source grain.
     */
    @Test
    @TestTransaction
    void imports_grouped_events_with_derived_rows() {
        seedContract();
        List<java.util.Map<String, String>> group1 = List.of(
                eventRow("G-1", "PREMIUM_COLLECTED", "100.00"),
                eventRow("G-1D", "PREMIUM_ISSUED", "50.00"));
        List<java.util.Map<String, String>> group2 = List.of(
                eventRow("G-2", "PREMIUM_COLLECTED", "80.00"),
                eventRow("G-2D", "", "40.00"));

        ImportBatch batch = service.importGroups("ALPTIS_ASSURANCES", "CLEVA", "grp.csv", "grp-hash",
                List.of(group1, group2), 0);

        assertEquals(2, batch.linesLoaded);
        assertEquals(0, batch.linesRejected);
        // Two events for group 1 (primary + derived), one for group 2 (derived skipped).
        assertEquals(3, BusinessEvent.count());
    }

    /**
     * Builds a pivot event row for the Alptis AD-1 / C1 coverage.
     *
     * @param key   the source key
     * @param type  the event type (empty to force a rejection)
     * @param gross the gross amount
     * @return the pivot row
     */
    private static java.util.Map<String, String> eventRow(String key, String type, String gross) {
        java.util.Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put("source_key", key);
        row.put("event_type", type);
        row.put("contract_number", "AD-1");
        row.put("coverage_code", "C1");
        row.put("event_date", "2020-01-05");
        row.put("gross_amount", gross);
        return row;
    }
}
