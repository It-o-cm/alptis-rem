package fr.alptis.remuneration.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * Tests the product-referential administration endpoints (REF-01, INT-24): creating insurers, ranges,
 * options and products with validity windows, ending a validity, and refusing an overlapping (duplicate)
 * creation, each partitioned by entity.
 */
@QuarkusTest
class ReferentialAdminServiceTest {

    @Inject
    TestDatabaseReset reset;

    /** Restores the baseline before each test. */
    @BeforeEach
    void resetBaseline() {
        reset.reset();
    }

    /** Posts a JSON body to a UI endpoint as the UNEP business administrator. */
    private static ValidatableResponse asUnep(String path, String body) {
        return given().auth().preemptive().basic("admin-unep", "admin-unep").contentType("application/json")
                .body(body).when().post(path).then();
    }

    /** Posts a JSON body to a UI endpoint as the Alptis business administrator. */
    private static ValidatableResponse asAlptis(String path, String body) {
        return given().auth().preemptive().basic("admin-alptis", "admin-alptis").contentType("application/json")
                .body(body).when().post(path).then();
    }

    /** Reads the insurers as the UNEP business administrator. */
    private static ValidatableResponse insurers() {
        return given().auth().preemptive().basic("admin-unep", "admin-unep").when().get("/api/ui/v1/insurers").then();
    }

    /**
     * An insurer is created with its validity window, then a second one of the same code is refused as a
     * validity overlap; a role is parsed, and the created insurer is listed with its dates.
     */
    @Test
    void creates_an_insurer_then_refuses_an_overlap() {
        asUnep("/api/ui/v1/insurers",
                "{\"code\":\"INS-A\",\"name\":\"Assureur A\",\"role\":\"RISK_CARRIER\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(204);
        insurers().body("find { it.code == 'INS-A' }.validFrom", is("2026-01-01"))
                .body("collect { it.code }", hasItem("INS-A"));
        asUnep("/api/ui/v1/insurers",
                "{\"code\":\"INS-A\",\"name\":\"Assureur A bis\",\"validFrom\":\"2026-06-01\"}")
                .statusCode(400);
    }

    /** Insurer creation refuses a blank code, a missing start, an end before the start and a bad role. */
    @Test
    void refuses_invalid_insurer_input() {
        asUnep("/api/ui/v1/insurers", "{\"code\":\"\",\"name\":\"X\",\"validFrom\":\"2026-01-01\"}").statusCode(400);
        asUnep("/api/ui/v1/insurers", "{\"code\":\"INS-B\",\"name\":\"X\"}").statusCode(400);
        asUnep("/api/ui/v1/insurers",
                "{\"code\":\"INS-B\",\"name\":\"X\",\"validFrom\":\"2026-06-01\",\"validTo\":\"2026-01-01\"}")
                .statusCode(400);
        asUnep("/api/ui/v1/insurers",
                "{\"code\":\"INS-B\",\"name\":\"X\",\"role\":\"BOGUS\",\"validFrom\":\"2026-01-01\"}").statusCode(400);
    }

    /** A range is created with a target, a duplicate is refused, and a bad target is rejected. */
    @Test
    void creates_a_range_and_validates_its_target() {
        asUnep("/api/ui/v1/ranges",
                "{\"code\":\"RNG-A\",\"label\":\"Gamme A\",\"target\":\"INDIVIDUAL\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(204);
        asUnep("/api/ui/v1/ranges", "{\"code\":\"RNG-A\",\"label\":\"Gamme A\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(400);
        asUnep("/api/ui/v1/ranges",
                "{\"code\":\"RNG-B\",\"label\":\"Gamme B\",\"target\":\"BOGUS\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(400);
    }

    /**
     * A product is created with and without a range and insurer; a duplicate is refused; an unknown or
     * out-of-scope entity is refused.
     */
    @Test
    void creates_a_product_and_guards_its_entity() {
        asUnep("/api/ui/v1/products",
                "{\"entityCode\":\"UNEP\",\"code\":\"PRD-A\",\"label\":\"Produit A\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(204);
        // With a range and insurer resolved by code.
        asUnep("/api/ui/v1/ranges", "{\"code\":\"RNG-P\",\"label\":\"Gamme P\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(204);
        asUnep("/api/ui/v1/insurers", "{\"code\":\"INS-P\",\"name\":\"Assureur P\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(204);
        asUnep("/api/ui/v1/products",
                "{\"entityCode\":\"UNEP\",\"code\":\"PRD-B\",\"label\":\"Produit B\",\"rangeCode\":\"RNG-P\","
                        + "\"insurerCode\":\"INS-P\",\"validFrom\":\"2026-01-01\"}").statusCode(204);
        asUnep("/api/ui/v1/products",
                "{\"entityCode\":\"UNEP\",\"code\":\"PRD-A\",\"label\":\"dup\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(400);
        asUnep("/api/ui/v1/products",
                "{\"entityCode\":\"NOPE\",\"code\":\"PRD-C\",\"label\":\"X\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(404);
        asAlptis("/api/ui/v1/products",
                "{\"entityCode\":\"UNEP\",\"code\":\"PRD-D\",\"label\":\"X\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(404);
    }

    /**
     * An option is created under its product; a duplicate is refused; an unknown, out-of-scope product or
     * bad option type is rejected.
     */
    @Test
    void creates_an_option_under_its_product() {
        asUnep("/api/ui/v1/products",
                "{\"entityCode\":\"UNEP\",\"code\":\"PRD-OPT\",\"label\":\"Produit opt\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(204);
        asUnep("/api/ui/v1/options",
                "{\"productCode\":\"PRD-OPT\",\"code\":\"OPT-A\",\"label\":\"Option A\",\"optionType\":\"BASE\","
                        + "\"validFrom\":\"2026-01-01\"}").statusCode(204);
        asUnep("/api/ui/v1/options",
                "{\"productCode\":\"PRD-OPT\",\"code\":\"OPT-A\",\"label\":\"dup\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(400);
        asUnep("/api/ui/v1/options",
                "{\"productCode\":\"PRD-OPT\",\"code\":\"OPT-B\",\"label\":\"X\",\"optionType\":\"BOGUS\","
                        + "\"validFrom\":\"2026-01-01\"}").statusCode(400);
        asUnep("/api/ui/v1/options",
                "{\"productCode\":\"NOPE\",\"code\":\"OPT-C\",\"label\":\"X\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(404);
        // Out of scope: the Alptis admin cannot add an option to a UNEP product.
        asAlptis("/api/ui/v1/options",
                "{\"productCode\":\"PRD-OPT\",\"code\":\"OPT-D\",\"label\":\"X\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(404);
    }

    /**
     * Optional fields left blank are treated as absent, a blank end date is open-ended, a bounded window
     * within range is accepted, and a missing (null) code is refused.
     */
    @Test
    void accepts_optional_fields_and_bounded_windows() {
        // A blank role and a bounded, ordered window are accepted.
        asUnep("/api/ui/v1/insurers",
                "{\"code\":\"INS-O\",\"name\":\"X\",\"role\":\"\",\"validFrom\":\"2026-01-01\","
                        + "\"validTo\":\"2026-12-31\"}").statusCode(204);
        // A blank end date is read as open-ended.
        asUnep("/api/ui/v1/insurers",
                "{\"code\":\"INS-P2\",\"name\":\"X\",\"validFrom\":\"2026-01-01\",\"validTo\":\"\"}").statusCode(204);
        // A blank target and a blank option type are treated as absent.
        asUnep("/api/ui/v1/ranges",
                "{\"code\":\"RNG-O\",\"label\":\"X\",\"target\":\"\",\"validFrom\":\"2026-01-01\"}").statusCode(204);
        asUnep("/api/ui/v1/products",
                "{\"entityCode\":\"UNEP\",\"code\":\"PRD-O\",\"label\":\"X\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(204);
        asUnep("/api/ui/v1/options",
                "{\"productCode\":\"PRD-O\",\"code\":\"OPT-O\",\"label\":\"X\",\"optionType\":\"\","
                        + "\"validFrom\":\"2026-01-01\"}").statusCode(204);
        // A missing code is refused.
        asUnep("/api/ui/v1/insurers", "{\"name\":\"X\",\"validFrom\":\"2026-01-01\"}").statusCode(400);
    }

    /** Each object kind's validity is ended; a null date, unknown type and unknown object are refused. */
    @Test
    void ends_a_validity_of_each_kind() {
        asUnep("/api/ui/v1/insurers", "{\"code\":\"INS-E\",\"name\":\"X\",\"validFrom\":\"2026-01-01\"}").statusCode(204);
        asUnep("/api/ui/v1/ranges", "{\"code\":\"RNG-E\",\"label\":\"X\",\"validFrom\":\"2026-01-01\"}").statusCode(204);
        asUnep("/api/ui/v1/products",
                "{\"entityCode\":\"UNEP\",\"code\":\"PRD-E\",\"label\":\"X\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(204);
        asUnep("/api/ui/v1/options",
                "{\"productCode\":\"PRD-E\",\"code\":\"OPT-E\",\"label\":\"X\",\"validFrom\":\"2026-01-01\"}")
                .statusCode(204);

        asUnep("/api/ui/v1/referentials/insurer/INS-E/end", "{\"validTo\":\"2026-05-31\"}").statusCode(204);
        asUnep("/api/ui/v1/referentials/range/RNG-E/end", "{\"validTo\":\"2026-05-31\"}").statusCode(204);
        asUnep("/api/ui/v1/referentials/product/PRD-E/end", "{\"entityCode\":\"UNEP\",\"validTo\":\"2026-05-31\"}")
                .statusCode(204);
        asUnep("/api/ui/v1/referentials/option/OPT-E/end", "{\"productCode\":\"PRD-E\",\"validTo\":\"2026-05-31\"}")
                .statusCode(204);
        insurers().body("find { it.code == 'INS-E' }.validTo", is("2026-05-31"));

        // Refusals: a missing date, an unknown type, an end before the start, an unknown object, and the
        // scope guards of an option's product and a product's entity.
        asUnep("/api/ui/v1/referentials/insurer/INS-E/end", "{}").statusCode(400);
        asUnep("/api/ui/v1/referentials/mystery/INS-E/end", "{\"validTo\":\"2026-05-31\"}").statusCode(400);
        asUnep("/api/ui/v1/referentials/insurer/INS-E/end", "{\"validTo\":\"2025-01-01\"}").statusCode(400);
        asUnep("/api/ui/v1/referentials/insurer/NOPE/end", "{\"validTo\":\"2026-05-31\"}").statusCode(404);
        asUnep("/api/ui/v1/referentials/option/NOPE/end", "{\"productCode\":\"NOPE\",\"validTo\":\"2026-05-31\"}")
                .statusCode(404);
        asUnep("/api/ui/v1/referentials/option/OPT-E/end", "{\"productCode\":\"PRD-E\",\"validTo\":\"2025-01-01\"}")
                .statusCode(400);
        asUnep("/api/ui/v1/referentials/product/PRD-E/end", "{\"entityCode\":\"NOPE\",\"validTo\":\"2026-05-31\"}")
                .statusCode(404);
        // Out of scope: the Alptis admin cannot end a UNEP product's option or the product itself.
        asAlptis("/api/ui/v1/referentials/option/OPT-E/end", "{\"productCode\":\"PRD-E\",\"validTo\":\"2026-05-31\"}")
                .statusCode(404);
        asAlptis("/api/ui/v1/referentials/product/PRD-E/end", "{\"entityCode\":\"UNEP\",\"validTo\":\"2026-05-31\"}")
                .statusCode(404);
    }
}
