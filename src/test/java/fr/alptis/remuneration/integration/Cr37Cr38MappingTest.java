package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.Contract;
import fr.alptis.remuneration.domain.EventType;
import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.LegalEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reference cases CR-37 and CR-38: the UMR mapping keeps only the entity's part and derives the
 * received insurer commission. CR-38 uses a hand-built file reproducing the bordereau totals
 * (see docs/DECISIONS.md).
 */
@QuarkusTest
class Cr37Cr38MappingTest {

    private static final String MAPPING = """
            {
              "filter": {"column": "NOM_MUTUELLE", "equals": "ALPTIS"},
              "fields": {
                "source_key": {"column": "CODE_MOUVEMENT"},
                "event_type": {"column": "TYPE_MOUVEMENT", "transcode": {"VI": "INITIAL_PAYMENT", "FG": "OUTSTANDING_FEE"}},
                "contract_number": {"column": "NUMERO_CONTRAT"},
                "event_date": {"column": "DATE_MOUVEMENT"}
              },
              "derived": [{
                "when": {"column": "MONTANT_COMMISSION"},
                "fields": {
                  "source_key": {"column": "CODE_MOUVEMENT", "suffix": "-COM"},
                  "event_type": {"const": "INSURER_COMMISSION"},
                  "contract_number": {"column": "NUMERO_CONTRAT"},
                  "event_date": {"column": "DATE_MOUVEMENT"},
                  "precomputed_amount": {"column": "MONTANT_COMMISSION"}
                }
              }]
            }
            """;

    private static final String HEADER =
            "CODE_MOUVEMENT;TYPE_MOUVEMENT;NUMERO_CONTRAT;DATE_MOUVEMENT;NOM_MUTUELLE;MONTANT_COMMISSION";

    @Inject
    MappingImportService service;

    private void seedContract() {
        LegalEntity alptis = LegalEntity.<LegalEntity>find("code", "ALPTIS_ASSURANCES").firstResult();
        Contract contract = new Contract();
        contract.entity = alptis;
        contract.sourceSystem = "UMR";
        contract.contractNumber = "AD-1";
        contract.persist();
    }

    /**
     * CR-37: for a management-fee line split between Alptis (0,24 €) and UMR (0,19 €), only the
     * Alptis part is integrated.
     */
    @Test
    @TestTransaction
    void cr37_only_the_entity_part_is_integrated() {
        seedContract();
        String content = HEADER + "\n"
                + "M1;FG;AD-1;2026-07-05;ALPTIS;0.24\n"
                + "M1;FG;AD-1;2026-07-05;UMR;0.19\n";

        ImportBatch batch = service.importMappedEvents("ALPTIS_ASSURANCES", "UMR", "umr.xlsx", content, MAPPING);

        assertEquals(2, batch.linesRead);
        assertEquals(1, batch.linesFiltered);
        assertEquals(1, batch.linesLoaded);

        BusinessEvent insurer = BusinessEvent.find("sourceKey", "M1-COM").firstResult();
        assertEquals(EventType.INSURER_COMMISSION, insurer.eventType);
        assertEquals(0, insurer.precomputedAmount.compareTo(new BigDecimal("0.24")));
        assertEquals(0, BusinessEvent.count("precomputedAmount", new BigDecimal("0.19")));
    }

    /**
     * CR-38: the received commissions equal the bordereau's "montant de commission dû" of 451,54 €
     * (449,08 on payments plus 2,46 on management fees), on a hand-built file.
     */
    @Test
    @TestTransaction
    void cr38_received_commissions_match_the_bordereau_total() {
        seedContract();
        String content = HEADER + "\n"
                + "M10;VI;AD-1;2026-07-05;ALPTIS;449.08\n"
                + "M11;FG;AD-1;2026-07-10;ALPTIS;2.46\n";

        service.importMappedEvents("ALPTIS_ASSURANCES", "UMR", "umr_prod_202607.xlsx", content, MAPPING);

        List<BusinessEvent> received = BusinessEvent.list("eventType", EventType.INSURER_COMMISSION);
        BigDecimal total = received.stream()
                .map(e -> e.precomputedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(new BigDecimal("451.54")));
    }
}
