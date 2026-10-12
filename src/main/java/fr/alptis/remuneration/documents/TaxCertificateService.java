package fr.alptis.remuneration.documents;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.security.EntityScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces the annual tax certificate per beneficiary (BRD-08): for a fiscal year, the total
 * commission paid to each distributor, and the certificate of one beneficiary. Partitioned by entity.
 */
@ApplicationScoped
public class TaxCertificateService {

    private final EntityScope scope;

    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param scope the entity partition
     * @param clock the application date/time provider
     */
    @Inject
    public TaxCertificateService(EntityScope scope, DateTimeProvider clock) {
        this.scope = scope;
        this.clock = clock;
    }

    /**
     * The fiscal year to certify by default: the current year.
     *
     * @return the current year
     */
    public int currentYear() {
        return clock.now().getYear();
    }

    /**
     * Lists the annual tax certificates of an entity for a fiscal year (BRD-08): one line per
     * beneficiary with the total commission paid to it that year (lines attached to the year and paid).
     *
     * @param entityCode the entity code
     * @param year       the fiscal year
     * @return the certificate views, by beneficiary
     * @throws ResourceNotFoundException if the entity is out of the caller's partition
     */
    @Transactional
    public List<CertificateView> certificates(String entityCode, int year) {
        LegalEntity entity = requireEntity(entityCode);
        Map<String, BigDecimal[]> byBeneficiary = new LinkedHashMap<>();
        for (CommissionLine line : paidLines(entity, year)) {
            String beneficiary = line.originDistributor == null ? "—" : line.originDistributor.partnerCode;
            BigDecimal[] totals = byBeneficiary.computeIfAbsent(beneficiary,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(line.amount);
            totals[1] = totals[1].add(BigDecimal.ONE);
        }
        List<CertificateView> views = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : byBeneficiary.entrySet()) {
            views.add(new CertificateView(entry.getKey(), year, entry.getValue()[0],
                    entry.getValue()[1].intValueExact()));
        }
        return views;
    }

    /**
     * Produces the tax certificate of one beneficiary for a fiscal year (BRD-08).
     *
     * @param entityCode  the entity code
     * @param year        the fiscal year
     * @param partnerCode the beneficiary partner code
     * @return the certificate
     * @throws ResourceNotFoundException if out of scope or the beneficiary has no paid commission
     */
    @Transactional
    public CertificateView certificate(String entityCode, int year, String partnerCode) {
        return certificates(entityCode, year).stream()
                .filter(c -> partnerCode.equals(c.beneficiary()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune commission payée pour " + partnerCode + " en " + year));
    }

    /**
     * The paid commission lines of an entity attached to a fiscal year.
     *
     * @param entity the entity
     * @param year   the fiscal year
     * @return the paid lines of the year
     */
    private static List<CommissionLine> paidLines(LegalEntity entity, int year) {
        List<CommissionLine> lines = new ArrayList<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list("entity = ?1 and status = ?2", entity,
                LineStatus.PAID)) {
            if (line.attachmentMonth != null && line.attachmentMonth.getYear() == year) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Loads an entity by code, enforcing the entity partition.
     *
     * @param entityCode the entity code
     * @return the entity
     */
    private LegalEntity requireEntity(String entityCode) {
        if (!scope.canSee(entityCode)) {
            throw new ResourceNotFoundException("Entité hors périmètre : " + entityCode);
        }
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        return entity;
    }

    /**
     * One beneficiary's annual tax certificate (BRD-08).
     *
     * @param beneficiary the beneficiary partner code
     * @param year        the fiscal year
     * @param total       the total commission paid that year
     * @param lineCount   the number of paid lines
     */
    public record CertificateView(String beneficiary, int year, BigDecimal total, int lineCount) {
    }
}
