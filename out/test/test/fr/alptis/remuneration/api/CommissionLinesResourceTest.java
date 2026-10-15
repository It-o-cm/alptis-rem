package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * HTTP tests for {@link CommissionLinesResource}: a commission line's detail with its explanation
 * (CAL-10), with the entity partition (an out-of-scope line and an unknown line are not found).
 */
@QuarkusTest
class CommissionLinesResourceTest {

    @Inject
    TestDatabaseReset reset;

    private long alptisLineId;
    private long unepLineId;

    /** Resets the database and seeds one Alptis line (with an explanation) and one UNEP line. */
    @BeforeEach
    void seed() {
        reset.reset();
        long[] ids = new long[2];
        QuarkusTransaction.requiringNew().run(() -> {
            ids[0] = line("ALPTIS_ASSURANCES", "{\"rate\":0.30}");
            ids[1] = line("UNEP", null);
        });
        alptisLineId = ids[0];
        unepLineId = ids[1];
    }

    /** Persists a bare earned commission line in an entity and returns its id. */
    private static long line(String entityCode, String explanation) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = LineStatus.EARNED;
        line.amount = new BigDecimal("12.00");
        line.explanation = explanation;
        line.persist();
        return line.getId();
    }

    /**
     * The detail of an Alptis line is returned with its explanation to an Alptis operator.
     */
    @Test
    void returns_the_line_detail() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/commission-lines/" + alptisLineId)
                .then().statusCode(200)
                .body("nature", is("LINEAR"))
                .body("explanation", is("{\"rate\":0.30}"));
    }

    /**
     * A line of another entity is out of scope, and an unknown line is not found.
     */
    @Test
    void out_of_scope_and_unknown_are_not_found() {
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/commission-lines/" + unepLineId)
                .then().statusCode(404);
        given().auth().preemptive().basic("operator-alptis", "operator-alptis")
                .when().get("/api/ui/v1/commission-lines/999999")
                .then().statusCode(404);
    }
}
