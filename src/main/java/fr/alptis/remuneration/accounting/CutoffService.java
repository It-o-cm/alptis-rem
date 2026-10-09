package fr.alptis.remuneration.accounting;

import fr.alptis.remuneration.domain.AccountingEntry;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.CutoffExtract;
import fr.alptis.remuneration.domain.CutoffStatus;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import io.quarkus.runtime.util.HashUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Cut-off service (section 10.4, FIN-03, FIN-09): the pilotage state read by coverage, distributor,
 * product or entity at a given date — forecast, accrued, adjustments, paid, balance to pay and left
 * to accrue — and the monthly accounting extraction (OUT-02) produced at closing, which freezes the
 * period's entries by stamping each with the cut-off extract that carries it (transmitted once).
 */
@ApplicationScoped
public class CutoffService {

    /** The acquisition natures whose lines accrue commission (the accrued column). */
    private static final Set<LineNature> ACQUIRED = Set.of(LineNature.LINEAR, LineNature.ACQUISITION,
            LineNature.SERVICING, LineNature.OUTSTANDING, LineNature.ARBITRAGE, LineNature.RETROCESSION,
            LineNature.EXTERNAL, LineNature.RCD, LineNature.RCP, LineNature.COMPLEMENT);

    /** The regularisation natures (reprises, escompte settlements, adjustments). */
    private static final Set<LineNature> REGULARISATION = Set.of(LineNature.CLAWBACK,
            LineNature.ADVANCE_ADJUSTMENT, LineNature.FEE, LineNature.MANUAL, LineNature.TRANSFER);

    /** The reading maille of the cut-off state. */
    public enum CutoffView {
        /** One row per coverage. */
        COVERAGE,
        /** One row per distributor account. */
        DISTRIBUTOR,
        /** One row per product. */
        PRODUCT,
        /** One row per entity. */
        ENTITY
    }

    /** The application date/time provider. */
    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public CutoffService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /** A mutable accumulator of the six cut-off figures for one group. */
    private static final class Bucket {
        /** The group label. */
        private final String label;
        /** Forecast cumulative. */
        private BigDecimal forecast = BigDecimal.ZERO;
        /** Accrued cumulative. */
        private BigDecimal earned = BigDecimal.ZERO;
        /** Adjustments cumulative. */
        private BigDecimal adjustments = BigDecimal.ZERO;
        /** Paid cumulative. */
        private BigDecimal paid = BigDecimal.ZERO;

        /**
         * Creates a bucket.
         *
         * @param label the group label
         */
        private Bucket(String label) {
            this.label = label;
        }
    }

    /**
     * Computes the cut-off state of an entity at a date, grouped by the given maille.
     *
     * @param entityCode the entity code
     * @param cutoffDate the as-of date
     * @param view       the reading maille
     * @return the cut-off report
     */
    public PilotageReport pilotage(String entityCode, LocalDate cutoffDate, CutoffView view) {
        LegalEntity entity = entity(entityCode);
        Function<CommissionLine, String> key = keyOf(view);
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and period.periodMonth <= ?2", entity, cutoffDate)) {
            Bucket bucket = buckets.computeIfAbsent(key.apply(line), Bucket::new);
            classify(bucket, line);
        }
        List<PilotageRow> rows = new ArrayList<>();
        Bucket total = new Bucket("Total affiché");
        for (Bucket bucket : buckets.values()) {
            rows.add(row(bucket));
            total.forecast = total.forecast.add(bucket.forecast);
            total.earned = total.earned.add(bucket.earned);
            total.adjustments = total.adjustments.add(bucket.adjustments);
            total.paid = total.paid.add(bucket.paid);
        }
        return new PilotageReport(view, rows, row(total));
    }

    /**
     * Adds a line to its bucket: a forecast line feeds the forecast, an accrued line the accrued or the
     * adjustments column by its nature, and a paid line feeds the paid column.
     *
     * @param bucket the bucket
     * @param line   the line
     */
    private void classify(Bucket bucket, CommissionLine line) {
        if (line.status == LineStatus.FORECAST) {
            bucket.forecast = bucket.forecast.add(line.amount);
            return;
        }
        if (ACQUIRED.contains(line.nature)) {
            bucket.earned = bucket.earned.add(line.amount);
        } else if (REGULARISATION.contains(line.nature)) {
            bucket.adjustments = bucket.adjustments.add(line.amount);
        }
        if (line.status == LineStatus.PAID) {
            bucket.paid = bucket.paid.add(line.amount);
        }
    }

    /**
     * Builds a row from a bucket, deriving the balance to pay and the amount left to accrue.
     *
     * @param bucket the bucket
     * @return the row
     */
    private PilotageRow row(Bucket bucket) {
        BigDecimal balance = bucket.earned.add(bucket.adjustments).subtract(bucket.paid);
        BigDecimal remaining = bucket.forecast.subtract(bucket.earned);
        return new PilotageRow(bucket.label, bucket.forecast, bucket.earned, bucket.adjustments, bucket.paid,
                balance, remaining);
    }

    /**
     * The grouping key and label function for a maille.
     *
     * @param view the maille
     * @return the key function
     */
    private Function<CommissionLine, String> keyOf(CutoffView view) {
        return switch (view) {
            case ENTITY -> line -> line.entity.name;
            case DISTRIBUTOR -> CutoffService::distributorLabel;
            case PRODUCT -> CutoffService::productLabel;
            case COVERAGE -> line -> line.coverage == null ? "—" : line.coverage.coverageCode;
        };
    }

    /**
     * The distributor-account label of a line, or a dash when it has no account.
     *
     * @param line the line
     * @return the label
     */
    private static String distributorLabel(CommissionLine line) {
        DistributorAccount account = line.account;
        return account == null ? "—" : account.payeeType + " #" + account.payeeId;
    }

    /**
     * The product label of a line, reached through the coverage option, or a dash.
     *
     * @param line the line
     * @return the label
     */
    private static String productLabel(CommissionLine line) {
        Coverage coverage = line.coverage;
        if (coverage == null || coverage.option == null) {
            return "—";
        }
        ProductOption option = coverage.option;
        Product product = option.product;
        return product == null ? "—" : product.label;
    }

    /**
     * Produces the monthly accounting extraction of a period (FIN-03, OUT-02): idempotent — an
     * existing extract is returned unchanged; otherwise the period's not-yet-carried entries are
     * stamped and the extract is created with the file reference and the fingerprint of the CSV.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the cut-off extract
     */
    @Transactional
    public CutoffExtract extract(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = entity(entityCode);
        Period period = period(entity, periodMonth);
        CutoffExtract existing = CutoffExtract.find("period = ?1", period).firstResult();
        if (existing != null) {
            return existing;
        }
        List<AccountingEntry> entries = AccountingEntry.list(
                "period = ?1 and cutoffExtract is null order by id", period);
        CutoffExtract extract = new CutoffExtract();
        extract.entity = entity;
        extract.period = period;
        extract.cutoffDate = periodMonth.withDayOfMonth(periodMonth.lengthOfMonth());
        extract.generatedAt = clock.now();
        extract.status = CutoffStatus.GENERATED;
        extract.fileRef = entity.code + "_ECR_" + periodMonth.getYear()
                + String.format("%02d", periodMonth.getMonthValue()) + ".csv";
        extract.fileHash = HashUtil.sha256(csv(entity, entries));
        extract.persist();
        for (AccountingEntry entry : entries) {
            entry.cutoffExtract = extract;
        }
        return extract;
    }

    /**
     * Returns the CSV of a period's extraction (OUT-02, section 9.11), recomputed identically from the
     * carried entries. Requires the extract to exist (produced at closing).
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month
     * @return the CSV content
     */
    public String extractCsv(String entityCode, LocalDate periodMonth) {
        LegalEntity entity = entity(entityCode);
        Period period = period(entity, periodMonth);
        CutoffExtract extract = CutoffExtract.find("period = ?1", period).firstResult();
        if (extract == null) {
            throw new ResourceNotFoundException("Aucune extraction pour la période");
        }
        List<AccountingEntry> entries = AccountingEntry.list(
                "cutoffExtract = ?1 order by id", extract);
        return csv(entity, entries);
    }

    /**
     * Builds the OUT-02 CSV from a list of entries (section 9.11 columns), one entry per line.
     *
     * @param entity  the entity
     * @param entries the entries
     * @return the CSV content
     */
    private String csv(LegalEntity entity, List<AccountingEntry> entries) {
        StringBuilder csv = new StringBuilder();
        csv.append("entity_code;fiscal_year;accounting_month;journal;entry_date;accounting_event;")
                .append("debit_account;credit_account;amount;payee_code;statement_number;batch_reference;")
                .append("premium_fiscal_year;line_id;end_to_end_id\n");
        for (AccountingEntry e : entries) {
            csv.append(entity.code).append(';')
                    .append(e.fiscalYear).append(';')
                    .append(e.accountingMonth).append(';')
                    .append(nullSafe(e.journal)).append(';')
                    .append(e.entryDate).append(';')
                    .append(e.accountingEvent).append(';')
                    .append(nullSafe(e.debitAccount)).append(';')
                    .append(nullSafe(e.creditAccount)).append(';')
                    .append(e.amount.toPlainString()).append(';')
                    .append(payeeCode(e)).append(';')
                    .append(statementNumber(e)).append(';')
                    .append(batchReference(e)).append(';')
                    .append(e.premiumFiscalYear == null ? "" : e.premiumFiscalYear).append(';')
                    .append(e.commissionLine == null ? "" : e.commissionLine.getId()).append(';')
                    .append(e.paymentOrder == null ? "" : nullSafe(e.paymentOrder.endToEndId)).append('\n');
        }
        return csv.toString();
    }

    /**
     * The beneficiary code carried by an entry's line account, or empty.
     *
     * @param entry the entry
     * @return the payee code
     */
    private String payeeCode(AccountingEntry entry) {
        if (entry.commissionLine == null || entry.commissionLine.account == null) {
            return "";
        }
        DistributorAccount account = entry.commissionLine.account;
        return account.payeeType + ":" + account.payeeId;
    }

    /**
     * The statement number carried by an entry's line, or empty.
     *
     * @param entry the entry
     * @return the statement number
     */
    private String statementNumber(AccountingEntry entry) {
        if (entry.commissionLine == null || entry.commissionLine.statement == null) {
            return "";
        }
        return nullSafe(entry.commissionLine.statement.statementNumber);
    }

    /**
     * The batch reference carried by an entry's order, or empty.
     *
     * @param entry the entry
     * @return the batch reference
     */
    private String batchReference(AccountingEntry entry) {
        if (entry.paymentOrder == null || entry.paymentOrder.batch == null) {
            return "";
        }
        return nullSafe(entry.paymentOrder.batch.batchReference);
    }

    /**
     * Returns the value, or an empty string when it is null (CSV convention: a missing value is empty).
     *
     * @param value the value
     * @return the value or an empty string
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Looks up an entity by code.
     *
     * @param code the entity code
     * @return the entity
     */
    private LegalEntity entity(String code) {
        LegalEntity entity = LegalEntity.find("code", code).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + code);
        }
        return entity;
    }

    /**
     * Looks up a period by entity and month.
     *
     * @param entity      the entity
     * @param periodMonth the period month
     * @return the period
     */
    private Period period(LegalEntity entity, LocalDate periodMonth) {
        Period period = Period.find("entity = ?1 and periodMonth = ?2", entity, periodMonth).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue");
        }
        return period;
    }

    /**
     * One row of the cut-off state.
     *
     * @param label       the group label
     * @param forecast    the forecast cumulative
     * @param earned      the accrued cumulative
     * @param adjustments the adjustments cumulative
     * @param paid        the paid cumulative
     * @param balance     the balance to pay (accrued + adjustments − paid)
     * @param remaining   the amount left to accrue (forecast − accrued)
     */
    public record PilotageRow(String label, BigDecimal forecast, BigDecimal earned, BigDecimal adjustments,
                              BigDecimal paid, BigDecimal balance, BigDecimal remaining) {
    }

    /**
     * The cut-off state: the maille, the rows and the displayed total.
     *
     * @param view  the reading maille
     * @param rows  the rows
     * @param total the displayed total
     */
    public record PilotageReport(CutoffView view, List<PilotageRow> rows, PilotageRow total) {
    }
}
