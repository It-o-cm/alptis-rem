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
        ACCOUNTS
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
        LegalEntity entity = entity(entityCode);
        String fileName = entity.code + "_DATA_" + object.name().toLowerCase() + "_" + fileDate() + ".csv";
        return journal(entity, "OUT-05:" + object.name(), fileName, build(entity, object, null), user);
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
        return journal(entity, flow, fileName, build(entity, object, periodMonth), user);
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
        LocalDate month = parts.length > 2 ? LocalDate.parse(parts[2]) : null;
        return build(batch.entity, object, month).content();
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
     * @return the built content
     */
    private Built build(LegalEntity entity, ExportObject object, LocalDate month) {
        return switch (object) {
            case COMMISSION_LINES -> commissionLines(entity, month);
            case ACCOUNTS -> accountsCsv(entity);
        };
    }

    /**
     * Builds the commission-lines CSV, optionally restricted to a period.
     *
     * @param entity the entity
     * @param month  the period month, or null
     * @return the built content
     */
    private Built commissionLines(LegalEntity entity, LocalDate month) {
        List<CommissionLine> lines = month == null
                ? CommissionLine.list("entity = ?1 order by id", entity)
                : CommissionLine.list("entity = ?1 and period.periodMonth = ?2 order by id", entity, month);
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
     * @return the built content
     */
    private Built accountsCsv(LegalEntity entity) {
        List<DistributorAccount> list = DistributorAccount.list("entity = ?1 order by id", entity);
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
