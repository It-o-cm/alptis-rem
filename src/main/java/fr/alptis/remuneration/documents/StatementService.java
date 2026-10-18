package fr.alptis.remuneration.documents;

import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.DateTimeProvider;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.Statement;
import fr.alptis.remuneration.domain.StatementStatus;
import io.quarkus.runtime.util.HashUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.Period;
import fr.alptis.remuneration.domain.ProductionCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Statement (bordereau) service (section 9.5, BRD): renders a beneficiary's statement — its lines
 * grouped into the rubrics of section 9.5, the carried balance on one line and the net — then
 * publishes it as an archived, hashed document that is never modified (BRD-06), offers its
 * spreadsheet form (BRD-01), and issues a rectificative version referring to the first.
 */
@ApplicationScoped
public class StatementService {

    /** The commission-line statuses carried on a statement. */
    private static final List<LineStatus> SHOWN = List.of(LineStatus.EARNED, LineStatus.PAYABLE, LineStatus.PAID);

    private final DateTimeProvider clock;

    /**
     * Creates the service.
     *
     * @param clock the application date/time provider
     */
    @Inject
    public StatementService(DateTimeProvider clock) {
        this.clock = clock;
    }

    /**
     * Renders a statement's content (section 9.5): its rubric rows and its footer totals.
     *
     * @param statementId the statement id
     * @return the statement content
     */
    public StatementContent render(Long statementId) {
        Statement statement = Statement.findById(statementId);
        if (statement == null) {
            throw new ResourceNotFoundException("Bordereau inconnu : " + statementId);
        }
        fr.alptis.remuneration.domain.StatementGrouping grouping = grouping(statement.account);
        boolean savingsStatement = statement.batchType == fr.alptis.remuneration.domain.BatchType.SAVINGS_CYCLE;
        // When the other lot of this account has its own statement, its lines render there, not here; when
        // it has none, its net was imputed on this (paying) lot and is shown as one imputation line (PAI-04).
        boolean otherLotHasStatement = fr.alptis.remuneration.domain.Statement.count(
                "account = ?1 and period = ?2 and batchType = ?3", statement.account, statement.period,
                savingsStatement ? fr.alptis.remuneration.domain.BatchType.CYCLE
                        : fr.alptis.remuneration.domain.BatchType.SAVINGS_CYCLE) > 0;
        List<Row> rows = new ArrayList<>();
        BigDecimal imputed = BigDecimal.ZERO;
        boolean anyImputed = false;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "account = ?1 and period = ?2 and status in ?3 order by id",
                statement.account, statement.period, SHOWN)) {
            if (savingsLine(line) != savingsStatement) {
                // A line of the other lot: imputed on this bordereau when the other lot has no statement,
                // otherwise shown on its own.
                if (!otherLotHasStatement) {
                    imputed = imputed.add(line.amount == null ? BigDecimal.ZERO : line.amount);
                    anyImputed = true;
                }
                continue;
            }
            String group = grouping == fr.alptis.remuneration.domain.StatementGrouping.NONE ? null
                    : groupValue(line, grouping);
            rows.add(new Row(section(line.nature), label(line), line.basisAmount, line.rate, line.amount, line.reason,
                    group));
        }
        if (anyImputed) {
            rows.add(new Row("Imputation",
                    savingsStatement ? "Lot de cycle" : "Lot épargne retraite", null, null, imputed, null, null));
        }
        return new StatementContent(statement.statementNumber, statement.versionNo,
                beneficiaryLabel(statement.account), statement.period.periodMonth,
                rows, statement.periodTotal, statement.carriedBalance, statement.netAmount, grouping.name(),
                statement.level == null ? null : statement.level.name());
    }

    /**
     * A readable label naming the statement's beneficiary at its level (BRD-03): the production code, the
     * distributor's legal name or the grouping's code — never a raw payee id.
     *
     * @param account the statement's account
     * @return the beneficiary label
     */
    private static String beneficiaryLabel(fr.alptis.remuneration.domain.DistributorAccount account) {
        return switch (account.payeeType) {
            case PRODUCTION_CODE -> {
                ProductionCode pc = ProductionCode.findById(account.payeeId);
                yield "Code de production " + (pc == null ? account.payeeId : pc.code);
            }
            case DISTRIBUTOR -> {
                fr.alptis.remuneration.domain.Distributor d =
                        fr.alptis.remuneration.domain.Distributor.findById(account.payeeId);
                yield "Distributeur " + (d == null ? account.payeeId : d.legalName);
            }
            case GROUP -> {
                fr.alptis.remuneration.domain.DistributorGroup g =
                        fr.alptis.remuneration.domain.DistributorGroup.findById(account.payeeId);
                yield "Groupement " + (g == null ? account.payeeId : g.code);
            }
        };
    }

    /**
     * Resolves the statement's beneficiary distributor and its bordereau declension preference (BRD-04),
     * defaulting to no declension when there is no distributor or no preference.
     *
     * @param account the statement's account
     * @return the declension preference
     */
    private static fr.alptis.remuneration.domain.StatementGrouping grouping(
            fr.alptis.remuneration.domain.DistributorAccount account) {
        fr.alptis.remuneration.domain.Distributor distributor = null;
        if (account.payeeType == fr.alptis.remuneration.domain.PayeeType.PRODUCTION_CODE) {
            ProductionCode pc = ProductionCode.findById(account.payeeId);
            distributor = pc == null ? null : pc.distributor;
        } else if (account.payeeType == fr.alptis.remuneration.domain.PayeeType.DISTRIBUTOR) {
            distributor = fr.alptis.remuneration.domain.Distributor.findById(account.payeeId);
        }
        return distributor == null || distributor.statementGrouping == null
                ? fr.alptis.remuneration.domain.StatementGrouping.NONE : distributor.statementGrouping;
    }

    /**
     * Returns a line's declension value — its insurer code or its product code — or null when the line
     * carries no product (BRD-04). Called only when a declension is requested.
     *
     * @param line     the commission line
     * @param grouping the declension preference (by insurer or by product)
     * @return the declension value, or null
     */
    private static String groupValue(CommissionLine line, fr.alptis.remuneration.domain.StatementGrouping grouping) {
        fr.alptis.remuneration.domain.ProductOption option = line.coverage == null ? null : line.coverage.option;
        fr.alptis.remuneration.domain.Product product = option == null ? null : option.product;
        if (product == null) {
            return null;
        }
        if (grouping == fr.alptis.remuneration.domain.StatementGrouping.BY_INSURER) {
            return product.insurer == null ? null : product.insurer.code;
        }
        return product.code;
    }

    /**
     * Produces the informative statement of a group member paid by its grouping (BRD-11): the member's
     * own share, grouped by rubric like a real statement, but never paid (the payment went to the
     * grouping). The rows are the commission lines carrying the member's production code as origin.
     *
     * @param entityCode  the entity code
     * @param month       the period month
     * @param memberCode  the member's production code
     * @return the informative statement content
     * @throws ResourceNotFoundException when the entity, period or production code is unknown
     */
    @Transactional
    public StatementContent memberStatement(String entityCode, LocalDate month, String memberCode) {
        LegalEntity entity = LegalEntity.<LegalEntity>find("code", entityCode).firstResult();
        if (entity == null) {
            throw new ResourceNotFoundException("Entité inconnue : " + entityCode);
        }
        Period period = Period.<Period>find("entity = ?1 and periodMonth = ?2", entity, month).firstResult();
        if (period == null) {
            throw new ResourceNotFoundException("Période inconnue : " + month);
        }
        ProductionCode code = ProductionCode.<ProductionCode>find("entity = ?1 and code = ?2", entity, memberCode)
                .firstResult();
        if (code == null) {
            throw new ResourceNotFoundException("Code de production inconnu : " + memberCode);
        }
        List<Row> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "originProductionCode = ?1 and period = ?2 and status in ?3 order by id", code, period, SHOWN)) {
            rows.add(new Row(section(line.nature), label(line), line.basisAmount, line.rate, line.amount, line.reason,
                    null));
            total = total.add(line.amount == null ? BigDecimal.ZERO : line.amount);
        }
        return new StatementContent("RELEVE-" + memberCode, null, "Code de production " + memberCode,
                period.periodMonth, rows, total, BigDecimal.ZERO, total,
                fr.alptis.remuneration.domain.StatementGrouping.NONE.name(),
                fr.alptis.remuneration.domain.StatementLevel.PRODUCTION_CODE.name());
    }

    /**
     * Produces a consolidated multi-entity bordereau for a distributor over a month (BRD-04): one
     * subtotal line per covered entity (the distributor's cycle net there) and the grand total, so a
     * distributor active in several entities gets a single bordereau with a subtotal per entity. The
     * covered entities are limited to those the caller may see (null for all).
     *
     * @param partnerCode  the distributor partner code
     * @param month        the period month
     * @param visibleCodes the entity codes the caller may see, or null for all entities
     * @return the consolidated content
     * @throws ResourceNotFoundException when the distributor is unknown
     */
    public StatementContent consolidated(String partnerCode, java.time.LocalDate month, List<String> visibleCodes) {
        fr.alptis.remuneration.domain.Distributor distributor =
                fr.alptis.remuneration.domain.Distributor.<fr.alptis.remuneration.domain.Distributor>find(
                        "partnerCode", partnerCode).firstResult();
        if (distributor == null) {
            throw new ResourceNotFoundException("Distributeur inconnu : " + partnerCode);
        }
        List<fr.alptis.remuneration.domain.Statement> statements = visibleCodes == null
                ? fr.alptis.remuneration.domain.Statement.list("period.periodMonth = ?1 and batchType = ?2",
                        month, fr.alptis.remuneration.domain.BatchType.CYCLE)
                : fr.alptis.remuneration.domain.Statement.list(
                        "period.periodMonth = ?1 and batchType = ?2 and entity.code in ?3", month,
                        fr.alptis.remuneration.domain.BatchType.CYCLE, visibleCodes);
        java.util.Map<String, BigDecimal> subtotals = new java.util.TreeMap<>();
        for (fr.alptis.remuneration.domain.Statement statement : statements) {
            fr.alptis.remuneration.domain.Distributor owner = distributorOf(statement.account);
            if (owner != null && owner.getId().equals(distributor.getId())) {
                subtotals.merge(statement.entity.name,
                        statement.netAmount == null ? BigDecimal.ZERO : statement.netAmount, BigDecimal::add);
            }
        }
        List<Row> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (java.util.Map.Entry<String, BigDecimal> entry : subtotals.entrySet()) {
            rows.add(new Row("Sous-total entité", entry.getKey(), null, null, entry.getValue(), null, null));
            total = total.add(entry.getValue());
        }
        return new StatementContent("CONSO-" + partnerCode + "-" + month, null,
                "Distributeur " + distributor.legalName, month, rows, total, BigDecimal.ZERO, total,
                fr.alptis.remuneration.domain.StatementGrouping.NONE.name(), null);
    }

    /**
     * Returns the execution date of a period's cycle lot, or null when there is none (BRD-04).
     *
     * @param period the statement's period
     * @return the cycle-lot execution date, or null
     */
    private static java.time.LocalDate cycleExecutionDate(Period period) {
        fr.alptis.remuneration.domain.PaymentBatch cycle = fr.alptis.remuneration.domain.PaymentBatch.find(
                "period = ?1 and batchType = ?2", period, fr.alptis.remuneration.domain.BatchType.CYCLE)
                .firstResult();
        return cycle == null ? null : cycle.executionDate;
    }

    /**
     * Resolves the distributor behind a statement's account (its production code's distributor, or the
     * distributor itself), or null for a grouping account.
     *
     * @param account the statement account
     * @return the distributor, or null
     */
    private static fr.alptis.remuneration.domain.Distributor distributorOf(
            fr.alptis.remuneration.domain.DistributorAccount account) {
        if (account == null) {
            return null;
        }
        if (account.payeeType == fr.alptis.remuneration.domain.PayeeType.PRODUCTION_CODE) {
            ProductionCode pc = ProductionCode.findById(account.payeeId);
            return pc == null ? null : pc.distributor;
        }
        if (account.payeeType == fr.alptis.remuneration.domain.PayeeType.DISTRIBUTOR) {
            return fr.alptis.remuneration.domain.Distributor.findById(account.payeeId);
        }
        return null;
    }

    /**
     * Publishes a statement: its real PDF is produced and archived under a reference and its SHA-256
     * hash (BRD-06), its spreadsheet reference is recorded, and it is marked published at the current
     * time. The archived document is never modified afterwards.
     *
     * @param statementId the statement id
     * @return the published statement
     */
    @Transactional
    public Statement publish(Long statementId) {
        Statement statement = Statement.findById(statementId);
        if (statement == null) {
            throw new ResourceNotFoundException("Bordereau inconnu : " + statementId);
        }
        byte[] rendered = StatementDocuments.pdf(render(statementId));
        String base = statement.entity.code + "_BORDEREAU_" + statement.statementNumber + "_v" + statement.versionNo;
        statement.pdfRef = base + ".pdf";
        // ISO-8859-1 maps every byte to a char losslessly, so this hashes the exact PDF content.
        statement.pdfHash = HashUtil.sha256(new String(rendered, java.nio.charset.StandardCharsets.ISO_8859_1));
        statement.sheetRef = base + ".xlsx";
        statement.publishedAt = clock.now();
        // The bordereau is published the day after the payment execution (section 7.1, BRD-04): the
        // publication date is the period's cycle-lot execution date plus one day, defaulting to the
        // current day when no execution date is set yet.
        java.time.LocalDate execution = cycleExecutionDate(statement.period);
        statement.publicationDate = execution == null ? clock.today() : execution.plusDays(1);
        // The metadata (number, references, hash) is deposited for the GED under this reference (BRD-10).
        statement.gedReference = "GED-" + base;
        statement.status = StatementStatus.PUBLISHED;
        return statement;
    }

    /**
     * Produces the real PDF of a statement (BRD-01).
     *
     * @param statementId the statement id
     * @return the PDF bytes
     */
    public byte[] pdf(Long statementId) {
        return StatementDocuments.pdf(render(statementId));
    }

    /**
     * Produces the real spreadsheet (xlsx) of a statement (BRD-01).
     *
     * @param statementId the statement id
     * @return the workbook bytes
     */
    public byte[] workbook(Long statementId) {
        return StatementDocuments.workbook(render(statementId));
    }

    /**
     * Returns the spreadsheet (CSV) form of a statement: one flat row per line.
     *
     * @param statementId the statement id
     * @return the CSV content
     */
    public String spreadsheet(Long statementId) {
        StatementContent content = render(statementId);
        StringBuilder csv = new StringBuilder("rubrique;libelle;assiette;taux;montant;motif;declinaison\n");
        for (Row row : content.rows()) {
            csv.append(row.section()).append(';').append(row.label()).append(';')
                    .append(plain(row.basis())).append(';').append(plain(row.rate())).append(';')
                    .append(plain(row.amount())).append(';').append(row.reason() == null ? "" : row.reason())
                    .append(';').append(row.group() == null ? "" : row.group())
                    .append('\n');
        }
        csv.append("Total de la période;;;;").append(plain(content.periodTotal())).append(";;\n");
        csv.append("Solde antérieur reporté;;;;").append(plain(content.carried())).append(";;\n");
        csv.append("Net payé;;;;").append(plain(content.net())).append(";;\n");
        return csv.toString();
    }

    /**
     * Issues a rectificative version of a published statement, referring to the first (BRD-06); a new
     * version is created in draft, to be published in turn.
     *
     * @param statementId the published statement id
     * @return the rectificative statement
     */
    @Transactional
    public Statement rectify(Long statementId) {
        Statement original = Statement.findById(statementId);
        if (original == null) {
            throw new ResourceNotFoundException("Bordereau inconnu : " + statementId);
        }
        Statement rectified = new Statement();
        rectified.entity = original.entity;
        rectified.period = original.period;
        rectified.account = original.account;
        rectified.batchType = original.batchType;
        rectified.statementNumber = original.statementNumber;
        rectified.versionNo = original.versionNo + 1;
        rectified.level = original.level;
        rectified.carriedBalance = original.carriedBalance;
        rectified.periodTotal = original.periodTotal;
        rectified.netAmount = original.netAmount;
        rectified.status = StatementStatus.DRAFT;
        rectified.replacesStatement = original;
        rectified.setCreatedAt(clock.now());
        rectified.persist();
        return rectified;
    }

    /**
     * Whether a commission line belongs to a retirement-savings range (section 10.5): such lines go to
     * the savings lot, the others to the cycle lot.
     *
     * @param line the commission line
     * @return true when its product's range is a retirement-savings range
     */
    private static boolean savingsLine(CommissionLine line) {
        return line.contract != null && line.contract.product != null
                && line.contract.product.range != null
                && Boolean.TRUE.equals(line.contract.product.range.retirementSavings);
    }

    /**
     * Returns the section 9.5 rubric of a line nature.
     *
     * @param nature the line nature
     * @return the rubric label
     */
    private static String section(LineNature nature) {
        return switch (nature) {
            case CLAWBACK -> "Reprises";
            case ADVANCE -> "Escomptes versés";
            case ADVANCE_ADJUSTMENT -> "Régularisations d'escompte";
            case COMPLEMENT, RCD, RCP -> "Rémunérations complémentaires";
            case MANUAL, TRANSFER -> "Ajustements";
            default -> "Commissions";
        };
    }

    /**
     * Returns a line's label: its coverage code when present, else its nature.
     *
     * @param line the line
     * @return the label
     */
    private static String label(CommissionLine line) {
        return line.coverage == null ? line.nature.name() : line.coverage.coverageCode;
    }

    /**
     * Formats a nullable decimal as a plain string, empty when null.
     *
     * @param value the value, possibly null
     * @return the plain string, or empty
     */
    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /**
     * One rubric row of a statement.
     *
     * @param section the rubric
     * @param label   the line label
     * @param basis   the basis, or null
     * @param rate    the rate, or null
     * @param amount  the amount
     * @param reason  the reason, or null
     * @param group   the declension value (insurer or product), or null when no declension (BRD-04)
     */
    public record Row(String section, String label, BigDecimal basis, BigDecimal rate, BigDecimal amount,
                      String reason, String group) {
    }

    /**
     * The rendered content of a statement.
     *
     * @param statementNumber the statement number
     * @param versionNo       the version
     * @param beneficiary     the beneficiary label
     * @param month           the period month
     * @param rows            the rubric rows
     * @param periodTotal     the period total
     * @param carried         the carried balance
     * @param net             the net amount
     * @param grouping        the bordereau declension preference (NONE, BY_INSURER, BY_PRODUCT, BRD-04)
     * @param level           the beneficiary level (PRODUCTION_CODE, DISTRIBUTOR, GROUP, BRD-03), or null
     */
    public record StatementContent(String statementNumber, Integer versionNo, String beneficiary,
                                   java.time.LocalDate month, List<Row> rows, BigDecimal periodTotal,
                                   BigDecimal carried, BigDecimal net, String grouping, String level) {
    }
}
