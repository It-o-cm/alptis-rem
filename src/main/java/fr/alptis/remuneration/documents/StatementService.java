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

import java.math.BigDecimal;
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
        List<Row> rows = new ArrayList<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "account = ?1 and period = ?2 and status in ?3 order by id",
                statement.account, statement.period, SHOWN)) {
            rows.add(new Row(section(line.nature), label(line), line.basisAmount, line.rate, line.amount, line.reason));
        }
        return new StatementContent(statement.statementNumber, statement.versionNo,
                statement.account.payeeType + " #" + statement.account.payeeId, statement.period.periodMonth,
                rows, statement.periodTotal, statement.carriedBalance, statement.netAmount);
    }

    /**
     * Publishes a statement: it is rendered, hashed and archived with its reference, and never
     * modified afterwards (BRD-06).
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
        String rendered = text(render(statementId));
        statement.pdfRef = statement.entity.code + "_BORDEREAU_" + statement.statementNumber
                + "_v" + statement.versionNo + ".pdf";
        statement.pdfHash = HashUtil.sha256(rendered);
        statement.status = StatementStatus.PUBLISHED;
        return statement;
    }

    /**
     * Returns the spreadsheet (CSV) form of a statement: one flat row per line.
     *
     * @param statementId the statement id
     * @return the CSV content
     */
    public String spreadsheet(Long statementId) {
        StatementContent content = render(statementId);
        StringBuilder csv = new StringBuilder("rubrique;libelle;assiette;taux;montant;motif\n");
        for (Row row : content.rows()) {
            csv.append(row.section()).append(';').append(row.label()).append(';')
                    .append(plain(row.basis())).append(';').append(plain(row.rate())).append(';')
                    .append(plain(row.amount())).append(';').append(row.reason() == null ? "" : row.reason())
                    .append('\n');
        }
        csv.append("Total de la période;;;;").append(plain(content.periodTotal())).append(";\n");
        csv.append("Solde antérieur reporté;;;;").append(plain(content.carried())).append(";\n");
        csv.append("Net payé;;;;").append(plain(content.net())).append(";\n");
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
     * Serialises a content to a stable text used to compute the archive hash.
     *
     * @param content the content
     * @return the text
     */
    private static String text(StatementContent content) {
        StringBuilder sb = new StringBuilder(content.statementNumber()).append('|').append(content.versionNo())
                .append('|').append(content.beneficiary()).append('|').append(content.month());
        for (Row row : content.rows()) {
            sb.append('|').append(row.section()).append(':').append(row.label()).append('=').append(plain(row.amount()));
        }
        sb.append("|T").append(plain(content.periodTotal())).append("|R").append(plain(content.carried()))
                .append("|N").append(plain(content.net()));
        return sb.toString();
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
     */
    public record Row(String section, String label, BigDecimal basis, BigDecimal rate, BigDecimal amount,
                      String reason) {
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
     */
    public record StatementContent(String statementNumber, Integer versionNo, String beneficiary,
                                   java.time.LocalDate month, List<Row> rows, BigDecimal periodTotal,
                                   BigDecimal carried, BigDecimal net) {
    }
}
