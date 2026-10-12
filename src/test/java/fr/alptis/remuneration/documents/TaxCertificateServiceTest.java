package fr.alptis.remuneration.documents;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.security.CurrentUser;
import fr.alptis.remuneration.security.DevIdentityProvider;
import fr.alptis.remuneration.security.EntityScope;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link TaxCertificateService}: the annual paid total per beneficiary, the year and status
 * filters, the unresolved beneficiary, and the entity partition (BRD-08). Runs against UNEP in
 * rolled-back transactions.
 */
@QuarkusTest
class TaxCertificateServiceTest {

    @Inject
    DateTimeProvider clock;

    /** Builds a service for the given visible entities. */
    private TaxCertificateService service(List<String> entities) {
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("operator-unep"))
                .addAttribute(DevIdentityProvider.ENTITIES_ATTRIBUTE, entities)
                .build();
        return new TaxCertificateService(new EntityScope(new CurrentUser(identity)), clock);
    }

    /** The UNEP entity. */
    private static LegalEntity unep() {
        return LegalEntity.<LegalEntity>find("code", "UNEP").firstResult();
    }

    /** Persists a commission line for the certificate test. */
    private static void line(LineStatus status, Distributor distributor, String amount, LocalDate attachmentMonth) {
        CommissionLine line = new CommissionLine();
        line.entity = unep();
        line.direction = Direction.OUTBOUND;
        line.nature = LineNature.LINEAR;
        line.status = status;
        line.amount = new BigDecimal(amount);
        line.originDistributor = distributor;
        line.attachmentMonth = attachmentMonth;
        line.persist();
    }

    /**
     * The annual certificate sums the paid lines of the year per beneficiary; lines of another year,
     * with no attachment month, or not yet paid are excluded; a line with no beneficiary groups under a
     * dash.
     */
    @Test
    @TestTransaction
    void sums_paid_commissions_by_beneficiary() {
        Distributor d1 = new Distributor();
        d1.partnerCode = "D1";
        d1.legalName = "D1";
        d1.persist();
        line(LineStatus.PAID, d1, "100.00", LocalDate.of(2026, 3, 1));
        line(LineStatus.PAID, d1, "50.00", LocalDate.of(2026, 6, 1));
        line(LineStatus.PAID, null, "20.00", LocalDate.of(2026, 1, 1));
        line(LineStatus.PAID, d1, "999.00", LocalDate.of(2025, 12, 1)); // another year
        line(LineStatus.PAID, d1, "7.00", null);                        // no attachment month
        line(LineStatus.EARNED, d1, "500.00", LocalDate.of(2026, 3, 1)); // not paid

        TaxCertificateService svc = service(List.of("UNEP"));
        List<TaxCertificateService.CertificateView> certs = svc.certificates("UNEP", 2026);
        var d1Cert = certs.stream().filter(c -> "D1".equals(c.beneficiary())).findFirst().orElseThrow();
        assertEquals(0, d1Cert.total().compareTo(new BigDecimal("150.00")));
        assertEquals(2, d1Cert.lineCount());
        assertEquals(2026, d1Cert.year());
        var none = certs.stream().filter(c -> "—".equals(c.beneficiary())).findFirst().orElseThrow();
        assertEquals(0, none.total().compareTo(new BigDecimal("20.00")));

        // The single-beneficiary certificate, and an unknown beneficiary.
        assertEquals(0, svc.certificate("UNEP", 2026, "D1").total().compareTo(new BigDecimal("150.00")));
        assertThrows(ResourceNotFoundException.class, () -> svc.certificate("UNEP", 2026, "NOPE"));
    }

    /**
     * The current year comes from the clock, and an out-of-scope entity is not found.
     */
    @Test
    @TestTransaction
    void current_year_and_scope() {
        TaxCertificateService svc = service(List.of("UNEP"));
        assertTrue(svc.currentYear() >= 2026);
        assertThrows(ResourceNotFoundException.class, () -> svc.certificates("ALPTIS_ASSURANCES", 2026));
        assertThrows(ResourceNotFoundException.class, () -> service(List.of("*")).certificates("GHOST", 2026));
    }
}
