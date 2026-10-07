package fr.alptis.remuneration.domain;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Boots the persistence context so Hibernate creates the schema for all 68 entities, which
 * validates every mapping and uniqueness constraint of section 5.7, and exercises a basic
 * persist / read round-trip including a {@code jsonb} column.
 */
@QuarkusTest
class DomainPersistenceTest {

    /**
     * Persists a legal entity with a JSON column and reads it back by natural key.
     */
    @Test
    @TestTransaction
    void persists_and_reads_a_legal_entity_with_json_column() {
        LegalEntity entity = new LegalEntity();
        entity.code = "ALPTIS_ASSURANCES";
        entity.name = "Alptis Assurances";
        entity.debtorIban = "FR7630006000011234567890189";
        entity.minPaymentAmount = new BigDecimal("10.00");
        entity.advanceSettlementDays = 360;
        entity.paymentCalendar = "{\"businessDays\":7}";
        entity.persist();
        assertNotNull(entity.getId());

        LegalEntity found = LegalEntity.find("code", "ALPTIS_ASSURANCES").firstResult();
        assertNotNull(found);
        assertEquals("Alptis Assurances", found.name);
        assertEquals("{\"businessDays\":7}", found.paymentCalendar);
        assertEquals(360, found.advanceSettlementDays);
    }

    /**
     * Verifies that the uniqueness constraint on the entity code is enforced by the schema.
     */
    @Test
    @TestTransaction
    void rejects_a_duplicate_entity_code() {
        LegalEntity first = new LegalEntity();
        first.code = "UNEP";
        first.name = "UNEP";
        first.persist();

        // With IDENTITY generation the insert happens on persist, so the duplicate fails there.
        LegalEntity duplicate = new LegalEntity();
        duplicate.code = "UNEP";
        duplicate.name = "UNEP bis";
        assertThrows(ConstraintViolationException.class, duplicate::persist);
    }
}
