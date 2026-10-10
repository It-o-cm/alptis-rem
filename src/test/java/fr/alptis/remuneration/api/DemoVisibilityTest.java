package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ImportBatchStatus;
import fr.alptis.remuneration.domain.PageResult;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-side visibility acceptance of the demonstration dataset (brief {@code DONNEES-DEMO.md} §6):
 * for every declared dev user and every {@code /api/ui/v1} list endpoint the user is allowed to call
 * (the server answers other than 403/401), the response is non-empty after loading the dataset.
 *
 * <p>The list of endpoints is read from the code by reflection over the {@code api} package — never
 * copied by hand — so it cannot drift from the resources. Endpoints whose emptiness is the normal
 * state of a fresh install are named explicitly below, each with its reason (§7). Month-parameterised
 * endpoints are considered visible as long as one month carries data, since the screen lets the user
 * choose the month.</p>
 */
@QuarkusTest
class DemoVisibilityTest {

    @Inject
    DemoDataService demo;
    @Inject
    TestDatabaseReset reset;

    /** Every dev user declared in application.properties (password equals the username). */
    private static final List<String> USERS = List.of(
            "it-admin", "auditor", "group-validator", "admin-alptis", "admin2-alptis", "operator-alptis",
            "validator-alptis", "finance-alptis", "viewer-alptis", "manager-alptis", "rep-alptis",
            "admin-unep", "operator-unep", "validator-unep", "finance-unep", "viewer-unep");

    /** Candidate months tried for month-parameterised endpoints (a screen is shown for a chosen month). */
    private static final List<String> MONTHS = List.of("2026-09-01", "2026-10-01", "2026-08-01", "2026-06-01");

    /**
     * Section-7 exceptions: endpoints whose emptiness is a normal state, named with their reason. None
     * are needed — every list endpoint the dataset exposes carries data for the users that may see it.
     */
    private static final Map<String, String> EXCEPTIONS = Map.of();

    /** Resets the baseline and loads the demonstration dataset before each test. */
    @BeforeEach
    void setUp() {
        reset.reset();
        demo.load();
    }

    /**
     * Every list endpoint a user may call returns data (unless named as a §7 exception).
     */
    @Test
    void every_visible_list_endpoint_is_non_empty_for_every_user() {
        List<String> endpoints = discoverListEndpoints();
        assertTrue(endpoints.size() >= 30, "expected to discover the UI list endpoints, found " + endpoints.size());

        List<String> misses = new ArrayList<>();
        for (String user : USERS) {
            String entity = primaryEntity(user);
            // The partially-loaded batch is the one carrying rejections and held events.
            ImportBatch partial = ImportBatch.<ImportBatch>find("entity.code = ?1 and status = ?2", entity,
                    ImportBatchStatus.PARTIALLY_LOADED).firstResult();
            Long batchId = partial == null ? 0L : partial.getId();
            for (String template : endpoints) {
                check(user, entity, batchId, template, misses);
            }
        }
        assertTrue(misses.isEmpty(), "Empty visible list endpoints:\n" + String.join("\n", misses));
    }

    /**
     * Checks one user/endpoint pair, accumulating a miss when a visible endpoint is empty.
     *
     * @param user     the user
     * @param entity   the user's primary entity
     * @param batchId  an import batch id of the entity
     * @param template the endpoint path template
     * @param misses   the accumulator of empty visible endpoints
     */
    private void check(String user, String entity, Long batchId, String template, List<String> misses) {
        List<String> months = template.contains("{month}") ? MONTHS : List.of(MONTHS.get(0));
        boolean anyAllowed = false;
        boolean anyNonEmpty = false;
        for (String month : months) {
            String path = substitute(template, entity, month, batchId);
            Response response = given().auth().preemptive().basic(user, user).when().get(path);
            if (response.statusCode() == 200) {
                anyAllowed = true;
                if (!isEmpty(response)) {
                    anyNonEmpty = true;
                }
            }
        }
        if (anyAllowed && !anyNonEmpty && !EXCEPTIONS.containsKey(template)) {
            misses.add(user + " -> " + template);
        }
    }

    /**
     * Substitutes an endpoint's path parameters with demonstration values.
     *
     * @param template the path template
     * @param entity   the entity code
     * @param month    the month
     * @param batchId  an import batch id
     * @return the resolved path
     */
    private static String substitute(String template, String entity, String month, Long batchId) {
        return template
                .replace("{entity}", entity)
                .replace("{entityCode}", entity)
                .replace("{month}", month)
                .replace("{planCode}", DemoCodes.DEMO_PLAN)
                .replace("{ruleCode}", DemoCodes.DEMO_PLAN + "_" + DemoCodes.F_RCD)
                .replace("{id}", String.valueOf(batchId))
                .replace("{partnerCode}", entity.equals(DemoCodes.ALPTIS) ? DemoCodes.CABINET : "P20001")
                .replace("{externalId}", entity.equals(DemoCodes.ALPTIS) ? "rep-alptis" : "EMP-" + entity + "-2");
    }

    /**
     * Tells whether a list or paginated response is empty.
     *
     * @param response the response
     * @return {@code true} if it carries no element
     */
    private static boolean isEmpty(Response response) {
        Object root = response.jsonPath().get("$");
        if (root instanceof List) {
            return ((List<?>) root).isEmpty();
        }
        List<?> items = response.jsonPath().getList("items");
        return items == null || items.isEmpty();
    }

    /**
     * Returns a user's primary entity: its first concrete entity, Alptis for a group-level user.
     *
     * @param user the user
     * @return the primary entity code
     */
    private static String primaryEntity(String user) {
        List<String> entities = given().auth().preemptive().basic(user, user).when().get("/api/ui/v1/me")
                .jsonPath().getList("entities");
        for (String entity : entities) {
            if (!"*".equals(entity)) {
                return entity;
            }
        }
        return DemoCodes.ALPTIS;
    }

    /**
     * Discovers, by reflection over the JAX-RS resource beans of the container, every
     * {@code /api/ui/v1} endpoint that returns a list or a paginated page.
     *
     * @return the full path templates of the list endpoints
     */
    private static List<String> discoverListEndpoints() {
        Set<String> endpoints = new LinkedHashSet<>();
        BeanManager beanManager = CDI.current().getBeanManager();
        for (Bean<?> bean : beanManager.getBeans(Object.class, Any.Literal.INSTANCE)) {
            Class<?> resource = bean.getBeanClass();
            Path classPath = resource.getAnnotation(Path.class);
            if (classPath == null) {
                continue;
            }
            for (Method method : resource.getDeclaredMethods()) {
                if (method.getAnnotation(GET.class) == null) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                if (returnType != List.class && returnType != PageResult.class) {
                    continue;
                }
                Path methodPath = method.getAnnotation(Path.class);
                String full = classPath.value() + (methodPath == null ? "" : methodPath.value());
                if (full.startsWith("/api/ui/v1")) {
                    endpoints.add(full);
                }
            }
        }
        return new ArrayList<>(endpoints);
    }
}
