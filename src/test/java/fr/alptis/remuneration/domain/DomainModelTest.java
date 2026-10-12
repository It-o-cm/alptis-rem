package fr.alptis.remuneration.domain;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers every enum and every persistent entity of the {@code domain} package by scanning the
 * compiled package directory: enums are round-tripped through {@code valueOf}, and each concrete
 * {@link BaseEntity} subclass is instantiated through its default constructor.
 */
class DomainModelTest {

    /**
     * Loads every class of the {@code fr.alptis.remuneration.domain} package from the classpath.
     *
     * @return the loaded classes
     * @throws Exception if the package directory cannot be read or a class cannot be loaded
     */
    private static List<Class<?>> domainClasses() throws Exception {
        // Resolve the main classes directory from a known production class, so the scan never
        // picks up the test-classes directory (which shares the same package path).
        URL location = LegalEntity.class.getProtectionDomain().getCodeSource().getLocation();
        assertNotNull(location, "code source of the domain classes must be known");
        File dir = new File(new File(location.toURI()), "fr/alptis/remuneration/domain");
        List<Class<?>> classes = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".class"));
        assertNotNull(files);
        for (File file : files) {
            String simple = file.getName().substring(0, file.getName().length() - ".class".length());
            if (simple.contains("$")) {
                continue;
            }
            classes.add(Class.forName("fr.alptis.remuneration.domain." + simple));
        }
        return classes;
    }

    /**
     * Verifies that every enum of the package exposes its constants and that {@code valueOf} returns
     * each one by name.
     *
     * @throws Exception if a class cannot be loaded or inspected
     */
    @Test
    void every_enum_round_trips_its_constants() throws Exception {
        int enumCount = 0;
        for (Class<?> clazz : domainClasses()) {
            if (!clazz.isEnum()) {
                continue;
            }
            enumCount += 1;
            Object[] constants = clazz.getEnumConstants();
            assertTrue(constants.length > 0, clazz.getSimpleName() + " must have constants");
            for (Object constant : constants) {
                Enum<?> value = (Enum<?>) constant;
                Object resolved = Enum.valueOf(clazz.asSubclass(Enum.class), value.name());
                assertEquals(constant, resolved);
            }
        }
        assertTrue(enumCount >= 70, "expected the full set of domain enums, found " + enumCount);
    }

    /**
     * Verifies that every concrete persistent entity can be created and starts with no technical id.
     *
     * @throws Exception if a class cannot be loaded or instantiated
     */
    @Test
    void every_entity_can_be_instantiated() throws Exception {
        int entityCount = 0;
        for (Class<?> clazz : domainClasses()) {
            if (!BaseEntity.class.isAssignableFrom(clazz)
                    || clazz.equals(BaseEntity.class)
                    || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            entityCount += 1;
            BaseEntity entity = (BaseEntity) clazz.getDeclaredConstructor().newInstance();
            assertNotNull(entity);
            assertEquals(0L, entity.getVersion());
            assertEquals(null, entity.getId());
        }
        assertEquals(70, entityCount,
                "the 68 tables of section 5.7 plus the control_checklist_item (CTL-03) and simulation_history "
                        + "(PAR-13) operational tables");
    }
}
