package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.DistributorAccount;
import fr.alptis.remuneration.domain.ExportBatch;
import fr.alptis.remuneration.domain.ExportBatchStatus;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import io.quarkus.runtime.util.HashUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Export service (sections 9.11, 9.12, REP-04, REP-05): produces the on-demand CSV of a list the user
 * is allowed to see and the nightly Data export (OUT-05), each inscribed in the export journal
 * ({@code export_batch}) with its fingerprint, line count and total. A file is produced once and
 * re-downloaded identically, recomputed from the append-only data. In profile {@code dev} the Data
 * export is a full snapshot per object (the {@code updated_at} incremental window of the production
 * platform is not maintained — hypotheses H-05, H-51); the on-demand export carries its period.
 */
@ApplicationScoped
public class ExportService {

    /** The exportable objects supported in profile {@code dev}. */
    public enum ExportObject {
        /** The commission lines. */
        COMMISSION_LINES,
        /** The distributor accounts and their balances. */
        ACCOUNTS,
        /** The level-2 and level-3 control results (warnings and blocking), for CTL-06. */
        CONTROL_RESULTS
    }

    /** The application date/time provider. */
    private final DateTimeProvider clock;

    /** The distributor-account service, for account balances. */
    private final AccountService accounts;

    /**
     * Creates the service.
     *
     * @param clock    the application date/time provider
     * @param accounts the distributor-account service
     */
    @Inject
    public ExportService(DateTimeProvider clock, AccountService accounts) {
        this.clock = clock;
        this.accounts = accounts;
    }

    /** The built content of an export file with its line count and total. */
    private record Built(String content, int lines, BigDecimal total) {
    }

    /**
     * Produces the nightly Data export (OUT-05) of an object for an entity: a full snapshot in profile
     * {@code dev}, journalled under the {@code OUT-05} flow.
     *
     * @param entityCode the entity code
     * @param object     the exported object
     * @param user       the acting user
     * @return the export journal row
     */
    @Transactional
    public ExportBatch dataExport(String entityCode, ExportObject object, String user) {
        return dataExport(entityCode, object, null, user);
    }

    /**
     * Feeds the Data platform (REP-05, OUT-05). A full snapshot when {@code since} is null; otherwise an
     * incremental export of the objects created or modified on or after {@code since}, as the daily feed
     * requires, relying on the objects' modification date.
     *
     * @param entityCode the entity code
     * @param object     the exported object
     * @param since      the incremental window start, or null for a full snapshot
     * @param user       the acting user
     * @return the export journal row
     */
    @Transactional
    public ExportBatch dataExport(String entityCode, ExportObject object, LocalDate since, String user) {
        LegalEntity entity = entity(entityCode);
        String tag = since == null ? fileDate() : "INCR_" + since;
        String fileName = entity.code + "_DATA_" + object.name().toLowerCase() + "_" + tag + ".csv";
        String flow = "OUT-05:" + object.name() + (since == null ? "" : ":INCR:" + since);
        return journal(entity, flow, fileName, build(entity, object, null, since), user);
    }

    /**
     * Produces an on-demand export (REP-04) of a list the caller sees, for a period, journalled under
     * the {@code OUT-04} flow.
     *
     * @param entityCode  the entity code
     * @param periodMonth the period month, or null for every period
     * @param object      the exported object
     * @param user        the acting user
     * @return the export journal row
     */
    @Transactional
    public ExportBatch listExport(String entityCode, LocalDate periodMonth, ExportObject object, String user) {
        LegalEntity entity = entity(entityCode);
        String suffix = periodMonth == null ? "ALL" : periodMonth.getYear()
                + String.format("%02d", periodMonth.getMonthValue());
        String flow = "OUT-04:" + object.name() + (periodMonth == null ? "" : ":" + periodMonth);
        String fileName = entity.code + "_" + object.name() + "_" + suffix + ".csv";
        return journal(entity, flow, fileName, build(entity, object, periodMonth, null), user);
    }

    /**
     * Re-serves the content of a journalled export, recomputed identically from the append-only data.
     *
     * @param batchId the export batch id
     * @return the CSV content
     */
    public String content(Long batchId) {
        ExportBatch batch = ExportBatch.findById(batchId);
        if (batch == null) {
            throw new ResourceNotFoundException("Export inconnu : " + batchId);
        }
        String[] parts = batch.flowCode.split(":");
        ExportObject object = ExportObject.valueOf(parts[1]);
        // OUT-05:OBJECT:INCR:<date> is an incremental Data export; OUT-04:OBJECT:<month> is a period list.
        boolean incremental = parts.length > 2 && "INCR".equals(parts[2]);
        LocalDate since = incremental ? LocalDate.parse(parts[3]) : null;
        LocalDate month = !incremental && parts.length > 2 ? LocalDate.parse(parts[2]) : null;
        return build(batch.entity, object, month, since).content();
    }

    /**
     * Lists the export journal of an entity, newest first.
     *
     * @param entityCode the entity code
     * @return the journal rows
     */
    public List<ExportView> list(String entityCode) {
        LegalEntity entity = entity(entityCode);
        return ExportBatch.<ExportBatch>list("entity = ?1 order by generatedAt desc", entity).stream()
                .map(b -> new ExportView(b.getId(), b.flowCode, b.fileName, b.fileHash, b.generatedBy,
                        b.linesWritten, b.totalAmount, b.status))
                .toList();
    }

    /**
     * Writes an export journal row from a built file.
     *
     * @param entity   the entity
     * @param flow     the flow code
     * @param fileName the file name
     * @param built    the built content
     * @param user     the acting user
     * @return the journal row
     */
    private ExportBatch journal(LegalEntity entity, String flow, String fileName, Built built, String user) {
        ExportBatch batch = new ExportBatch();
        batch.entity = entity;
        batch.flowCode = flow;
        batch.fileName = fileName;
        batch.fileHash = HashUtil.sha256(built.content());
        batch.generatedAt = clock.now();
        batch.generatedBy = user;
        batch.linesWritten = built.lines();
        batch.totalAmount = built.total();
        batch.status = ExportBatchStatus.GENERATED;
        batch.persist();
        return batch;
    }

    /**
     * Builds the CSV of an object for an entity, optionally restricted to a period.
     *
     * @param entity the entity
     * @param object the object
     * @param month  the period month, or null
     * @param since  the incremental window start, or null
     * @return the built content
     */
    private Built build(LegalEntity entity, ExportObject object, LocalDate month, LocalDate since) {
        return switch (object) {
            case COMMISSION_LINES -> commissionLines(entity, month, since);
            case ACCOUNTS -> accountsCsv(entity, since);
            case CONTROL_RESULTS -> controlResultsCsv(entity, month, since);
        };
    }

    /**
     * The start-of-day timestamp of a window date, in the application clock's offset, used as the lower
     * bound of an incremental export.
     *
     * @param since the window date
     * @return the start-of-day timestamp
     */
    private java.time.OffsetDateTime startOf(LocalDate since) {
        return since.atStartOfDay().atOffset(clock.now().getOffset());
    }

    /**
     * Builds the CSV of the level-2 and level-3 control results (warnings and blocking controls) of an
     * entity, optionally restricted to a period (CTL-06): one row per result with its control, severity,
     * checked object, observed and expected values and decision.
     *
     * @param entity the entity
     * @param month  the period month, or null for every period
     * @return the built content
     */
    private Built controlResultsCsv(LegalEntity entity, LocalDate month, LocalDate since) {
        List<fr.alptis.remuneration.domain.ControlResult> results;
        if (month != null) {
            results = fr.alptis.remuneration.domain.ControlResult.list(
                    "period.entity = ?1 and period.periodMonth = ?2 and control.severity in ?3 order by id",
                    entity, month, LEVEL_2_3);
        } else if (since != null) {
            results = fr.alptis.remuneration.domain.ControlResult.list(
                    "period.entity = ?1 and control.severity in ?2 and (updatedAt >= ?3 or (updatedAt is null "
                            + "and createdAt >= ?3)) order by id", entity, LEVEL_2_3, startOf(since));
        } else {
            results = fr.alptis.remuneration.domain.ControlResult.list(
                    "period.entity = ?1 and control.severity in ?2 order by id", entity, LEVEL_2_3);
        }
        StringBuilder csv = new StringBuilder(
                "control_code;severity;object_type;object_id;observed;expected;decision\n");
        for (fr.alptis.remuneration.domain.ControlResult r : results) {
            csv.append(r.control.code).append(';')
                    .append(r.severity).append(';')
                    .append(r.objectType == null ? "" : r.objectType).append(';')
                    .append(r.objectId == null ? "" : r.objectId).append(';')
                    .append(r.observedValue == null ? "" : r.observedValue).append(';')
                    .append(r.expectedValue == null ? "" : r.expectedValue).append(';')
                    .append(r.decision).append('\n');
        }
        return new Built(csv.toString(), results.size(), BigDecimal.ZERO);
    }

    /** The level-2 (warning) and level-3 (blocking) control severities (CTL-06). */
    private static final List<fr.alptis.remuneration.domain.ControlSeverity> LEVEL_2_3 = List.of(
            fr.alptis.remuneration.domain.ControlSeverity.WARNING,
            fr.alptis.remuneration.domain.ControlSeverity.BLOCKING);

    /**
     * Builds the commission-lines CSV, optionally restricted to a period.
     *
     * @param entity the entity
     * @param month  the period month, or null
     * @return the built content
     */
    private Built commissionLines(LegalEntity entity, LocalDate month, LocalDate since) {
        List<CommissionLine> lines;
        if (month != null) {
            lines = CommissionLine.list("entity = ?1 and period.periodMonth = ?2 order by id", entity, month);
        } else if (since != null) {
            lines = CommissionLine.list("entity = ?1 and (updatedAt >= ?2 or (updatedAt is null "
                    + "and createdAt >= ?2)) order by id", entity, startOf(since));
        } else {
            lines = CommissionLine.list("entity = ?1 order by id", entity);
        }
        StringBuilder csv = new StringBuilder("line_id;nature;status;basis;rate;amount;payee_type;payee_id\n");
        BigDecimal total = BigDecimal.ZERO;
        for (CommissionLine line : lines) {
            DistributorAccount account = line.account;
            csv.append(line.getId()).append(';')
                    .append(line.nature).append(';')
                    .append(line.status).append(';')
                    .append(line.basisAmount == null ? "" : line.basisAmount.toPlainString()).append(';')
                    .append(line.rate == null ? "" : line.rate.toPlainString()).append(';')
                    .append(line.amount.toPlainString()).append(';')
                    .append(account == null ? "" : account.payeeType).append(';')
                    .append(account == null ? "" : account.payeeId).append('\n');
            total = total.add(line.amount);
        }
        return new Built(csv.toString(), lines.size(), total);
    }

    /**
     * Builds the accounts-and-balances CSV of an entity.
     *
     * @param entity the entity
     * @param since  the incremental window start, or null for every account
     * @return the built content
     */
    private Built accountsCsv(LegalEntity entity, LocalDate since) {
        List<DistributorAccount> list = since == null
                ? DistributorAccount.list("entity = ?1 order by id", entity)
                : DistributorAccount.list("entity = ?1 and (updatedAt >= ?2 or (updatedAt is null "
                        + "and createdAt >= ?2)) order by id", entity, startOf(since));
        StringBuilder csv = new StringBuilder("payee_type;payee_id;blocked;balance\n");
        BigDecimal total = BigDecimal.ZERO;
        for (DistributorAccount account : list) {
            BigDecimal balance = accounts.balance(account).setScale(2, java.math.RoundingMode.HALF_UP);
            csv.append(account.payeeType).append(';')
                    .append(account.payeeId).append(';')
                    .append(Boolean.TRUE.equals(account.blocked)).append(';')
                    .append(balance.toPlainString()).append('\n');
            total = total.add(balance);
        }
        return new Built(csv.toString(), list.size(), total);
    }

    /**
     * The export file date (yyyyMMdd) from the application clock.
     *
     * @return the file date
     */
    private String fileDate() {
        LocalDate today = clock.now().toLocalDate();
        return today.getYear() + String.format("%02d%02d", today.getMonthValue(), today.getDayOfMonth());
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
     * One row of the export journal.
     *
     * @param id        the batch id
     * @param flowCode  the flow code
     * @param fileName  the file name
     * @param fileHash  the fingerprint
     * @param author    the acting user
     * @param lines     the line count
     * @param total     the total amount
     * @param status    the status
     */
    public record ExportView(Long id, String flowCode, String fileName, String fileHash, String author,
                             Integer lines, BigDecimal total, ExportBatchStatus status) {
    }
}
