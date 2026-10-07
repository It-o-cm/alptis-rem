package fr.alptis.remuneration.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link BaseEntity}, exercised through a minimal concrete subclass.
 */
class BaseEntityTest {

    /** Minimal concrete entity used to instantiate the abstract base class under test. */
    static final class SampleEntity extends BaseEntity {
    }

    /**
     * Verifies that every common column accessor round-trips its value and starts null/zero.
     */
    @Test
    void accessors_round_trip_all_common_columns() {
        SampleEntity entity = new SampleEntity();
        assertNull(entity.getId());
        assertEquals(0L, entity.getVersion());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getCreatedBy());
        assertNull(entity.getUpdatedAt());

        OffsetDateTime created = OffsetDateTime.of(2026, 9, 1, 8, 0, 0, 0, ZoneOffset.ofHours(2));
        OffsetDateTime updated = OffsetDateTime.of(2026, 9, 2, 9, 30, 0, 0, ZoneOffset.ofHours(2));

        entity.setId(42L);
        entity.setVersion(3L);
        entity.setCreatedAt(created);
        entity.setCreatedBy("operator@alptis");
        entity.setUpdatedAt(updated);

        assertEquals(42L, entity.getId());
        assertEquals(3L, entity.getVersion());
        assertEquals(created, entity.getCreatedAt());
        assertEquals("operator@alptis", entity.getCreatedBy());
        assertEquals(updated, entity.getUpdatedAt());
    }
}
