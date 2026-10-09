package fr.alptis.remuneration.api;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.PayeeType;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.PeriodState;
import fr.alptis.remuneration.domain.ProductionCode;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Seeds a deterministic cycle scene for the end-to-end suite, outside production: a calculated
 * period, a distributor with a debtor and a creditor production-code account, so the period,
 * controls, adjustments and distributor-account screens have real data to exercise. Every business
 * action in the suite still goes through the regular interface; this only plants the starting state.
 */
@ApplicationScoped
@UnlessBuildProfile("prod")
public class CycleDemoSeeder {

    /** The partner code of the demo distributor. */
    public static final String PARTNER = "P13897";

    private final DateTimeProvider clock;

    /**
     * Creates the seeder.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public CycleDemoSeeder(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Plants the cycle scene of an entity's month.
     *
     * @param entityCode the entity code
     * @param month      the period month
     */
    @Transactional
    public void seed(String entityCode, LocalDate month) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            return;
        }
        Period period = new Period();
        period.entity = entity;
        period.periodMonth = month;
        period.state = PeriodState.CALCULATED;
        period.setCreatedAt(clock.now());
        period.persist();
        Distributor distributor = new Distributor();
        distributor.partnerCode = PARTNER;
        distributor.legalName = "Cabinet Martin";
        distributor.persist();
        account(entity, period, distributor, "PC-DEBIT", "-100");
        account(entity, period, distributor, "PC-CREDIT", "500");
    }

    /**
     * Creates a production code, its account and a commission line of the given amount.
     *
     * @param entity      the entity
     * @param period      the period
     * @param distributor the distributor
     * @param code        the production code
     * @param amount      the line amount
     */
    private void account(LegalEntity entity, Period period, Distributor distributor, String code, String amount) {
        ProductionCode pc = new ProductionCode();
        pc.entity = entity;
        pc.code = code;
        pc.distributor = distributor;
        pc.persist();
        DistributorAccount account = new DistributorAccount();
        account.entity = entity;
        account.payeeType = PayeeType.PRODUCTION_CODE;
        account.payeeId = pc.getId();
        account.setCreatedAt(clock.now());
        account.persist();
        CommissionLine line = new CommissionLine();
        line.entity = entity;
        line.period = period;
        line.account = account;
        line.direction = Direction.OUTBOUND;
        line.status = LineStatus.EARNED;
        line.nature = LineNature.LINEAR;
        line.amount = new BigDecimal(amount);
        line.attachmentMonth = month(period);
        line.setCreatedAt(clock.now());
        line.persist();
    }

    /**
     * Returns the period's month.
     *
     * @param period the period
     * @return the first day of the period month
     */
    private static LocalDate month(Period period) {
        return period.periodMonth;
    }
}
