package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.LegalEntity;
import fr.alptis.remuneration.domain.LineNature;
import fr.alptis.remuneration.domain.LineStatus;
import fr.alptis.remuneration.domain.ProcessingStatus;
import fr.alptis.remuneration.domain.Product;
import fr.alptis.remuneration.domain.ProductOption;
import fr.alptis.remuneration.domain.ResourceNotFoundException;
import fr.alptis.remuneration.domain.RuleVersion;
import fr.alptis.remuneration.domain.VersionStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reporting read model (section 8.12): the dashboards (REP-01), the "paid to whom, when, why"
 * cross-search (REP-02/03), the group consolidation (REP-07), the operating indicators (REP-09) and
 * the margin by product (REP-08). It only aggregates already-decided lines, accounts and events; it
 * decides and writes nothing.
 */
@ApplicationScoped
public class ReportingService {

    /** The statuses that count as commission (acquired, payable, paid). */
    private static final List<LineStatus> COUNTED = List.of(LineStatus.EARNED, LineStatus.PAYABLE,
            LineStatus.PAID);

    /** The distributor-account service, for debtor accounts. */
    private final AccountService accounts;

    /**
     * Creates the service.
     *
     * @param accounts the distributor-account service
     */
    @Inject
    public ReportingService(AccountService accounts) {
        this.accounts = accounts;
    }

    /**
     * Builds the dashboards of an entity, optionally restricted to a month (REP-01).
     *
     * @param entityCode the entity code
     * @param month      the period month, or null for every period
     * @return the dashboard view
     */
    public Dashboard dashboard(String entityCode, LocalDate month) {
        LegalEntity entity = entity(entityCode);
        List<CommissionLine> lines = countedLines(entity, month);
        Map<String, BigDecimal> byNature = new LinkedHashMap<>();
        Map<String, BigDecimal> byDistributor = new LinkedHashMap<>();
        BigDecimal reprises = BigDecimal.ZERO;
        BigDecimal escomptesVerses = BigDecimal.ZERO;
        for (CommissionLine line : lines) {
            byNature.merge(line.nature.name(), line.amount, BigDecimal::add);
            String distributor = line.originDistributor == null ? "—" : line.originDistributor.partnerCode;
            byDistributor.merge(distributor, line.amount, BigDecimal::add);
            if (line.nature == LineNature.CLAWBACK) {
                reprises = reprises.add(line.amount);
            } else if (line.nature == LineNature.ADVANCE) {
                escomptesVerses = escomptesVerses.add(line.amount);
            }
        }
        BigDecimal escomptesApures = offsetTotal(entity, month);
        List<Amount> natures = amounts(byNature);
        List<Amount> top = amounts(byDistributor).stream()
                .sorted(Comparator.comparing((Amount a) -> a.amount().abs()).reversed())
                .limit(5).toList();
        long debtors = accounts.debtors(entity).size();
        long anomalies = BusinessEvent.count("entity = ?1 and processingStatus = ?2", entity,
                ProcessingStatus.ON_HOLD);
        return new Dashboard(entityCode, natures, reprises, escomptesVerses, escomptesApures, debtors, top,
                anomalies);
    }

    /**
     * Builds the operating indicators of an entity (REP-09).
     *
     * @param entityCode the entity code
     * @return the indicators view
     */
    public Indicators indicators(String entityCode) {
        LegalEntity entity = entity(entityCode);
        long activeRules = RuleVersion.count("rule.plan.entity = ?1 and status = ?2", entity,
                VersionStatus.ACTIVE);
        long total = CommissionLine.count("entity = ?1", entity);
        long manual = CommissionLine.count("entity = ?1 and nature = ?2", entity, LineNature.MANUAL);
        BigDecimal manualShare = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(manual).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        BigDecimal clawback = total(entity, LineNature.CLAWBACK);
        return new Indicators(entityCode, activeRules, manualShare, clawback);
    }

    /**
     * Builds the consolidated group view: the commission total of every entity (REP-07). Reserved to
     * cross-entity users; the caller's scope is checked by the resource.
     *
     * @return the per-entity summaries
     */
    public List<EntitySummary> consolidated() {
        List<EntitySummary> summaries = new ArrayList<>();
        for (LegalEntity entity : LegalEntity.<LegalEntity>listAll()) {
            BigDecimal total = BigDecimal.ZERO;
            for (CommissionLine line : countedLines(entity, null)) {
                total = total.add(line.amount);
            }
            summaries.add(new EntitySummary(entity.code, entity.name, total));
        }
        return summaries;
    }

    /**
     * Answers "paid to whom, when, why": the commission lines of an entity filtered by product,
     * distributor, nature and month, each carrying its rule and bordereau references (REP-02/03,
     * FIN-07).
     *
     * @param entityCode  the entity code
     * @param product     the product code filter, or null
     * @param distributor the distributor partner-code filter, or null
     * @param nature      the nature filter, or null
     * @param month       the period month filter, or null
     * @return the matching rows
     */
    public List<SearchRow> search(String entityCode, String product, String distributor, LineNature nature,
                                  LocalDate month) {
        LegalEntity entity = entity(entityCode);
        List<SearchRow> rows = new ArrayList<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list("entity = ?1 order by id", entity)) {
            if (!COUNTED.contains(line.status)
                    || (nature != null && line.nature != nature)
                    || (month != null && (line.period == null || !line.period.periodMonth.equals(month)))
                    || (distributor != null && !distributor.equals(distributorCode(line)))
                    || (product != null && !product.equals(productCode(line)))) {
                continue;
            }
            rows.add(new SearchRow(distributorCode(line), productCode(line), line.nature.name(),
                    line.basisAmount, line.rate, line.amount, ruleCode(line), statementNumber(line),
                    line.period == null ? null : line.period.periodMonth));
        }
        return rows;
    }

    /**
     * Builds the margin by product (REP-08): the commission received (inbound) minus the commission
     * paid (outbound) for each product.
     *
     * @param entityCode the entity code
     * @return the margin rows
     */
    public List<MarginRow> margin(String entityCode) {
        LegalEntity entity = entity(entityCode);
        Map<String, BigDecimal[]> byProduct = new LinkedHashMap<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list("entity = ?1 and status in ?2", entity,
                COUNTED)) {
            BigDecimal[] totals = byProduct.computeIfAbsent(productCode(line),
                    k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            if (line.direction == Direction.INBOUND) {
                totals[0] = totals[0].add(line.amount);
            } else {
                totals[1] = totals[1].add(line.amount);
            }
        }
        List<MarginRow> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : byProduct.entrySet()) {
            BigDecimal[] totals = entry.getValue();
            rows.add(new MarginRow(entry.getKey(), totals[0], totals[1], totals[0].subtract(totals[1])));
        }
        return rows;
    }

    /**
     * The counted lines of an entity, optionally restricted to a month.
     *
     * @param entity the entity
     * @param month  the month, or null
     * @return the counted lines
     */
    private List<CommissionLine> countedLines(LegalEntity entity, LocalDate month) {
        if (month == null) {
            return CommissionLine.list("entity = ?1 and status in ?2", entity, COUNTED);
        }
        return CommissionLine.list("entity = ?1 and status in ?2 and period.periodMonth = ?3", entity, COUNTED,
                month);
    }

    /**
     * The sum of the offset (apurement) lines of an entity, optionally restricted to a month.
     *
     * @param entity the entity
     * @param month  the month, or null
     * @return the offset total
     */
    private BigDecimal offsetTotal(LegalEntity entity, LocalDate month) {
        List<CommissionLine> lines = month == null
                ? CommissionLine.list("entity = ?1 and status = ?2", entity, LineStatus.OFFSET)
                : CommissionLine.list("entity = ?1 and status = ?2 and period.periodMonth = ?3", entity,
                        LineStatus.OFFSET, month);
        BigDecimal total = BigDecimal.ZERO;
        for (CommissionLine line : lines) {
            total = total.add(line.amount);
        }
        return total;
    }

    /**
     * The total amount of a nature for an entity across counted statuses.
     *
     * @param entity the entity
     * @param nature the nature
     * @return the total
     */
    private BigDecimal total(LegalEntity entity, LineNature nature) {
        BigDecimal total = BigDecimal.ZERO;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and nature = ?2 and status in ?3", entity, nature, COUNTED)) {
            total = total.add(line.amount);
        }
        return total;
    }

    /**
     * Converts an amount map to a sorted list of amount rows.
     *
     * @param map the map
     * @return the amount rows
     */
    private List<Amount> amounts(Map<String, BigDecimal> map) {
        List<Amount> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : map.entrySet()) {
            list.add(new Amount(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    /**
     * The distributor partner code of a line, or a dash.
     *
     * @param line the line
     * @return the code
     */
    private String distributorCode(CommissionLine line) {
        return line.originDistributor == null ? "—" : line.originDistributor.partnerCode;
    }

    /**
     * The product code of a line, reached through the coverage option, or a dash.
     *
     * @param line the line
     * @return the code
     */
    private String productCode(CommissionLine line) {
        Coverage coverage = line.coverage;
        if (coverage == null || coverage.option == null) {
            return "—";
        }
        ProductOption option = coverage.option;
        Product product = option.product;
        return product == null ? "—" : product.code;
    }

    /**
     * The rule code of a line, or a dash.
     *
     * @param line the line
     * @return the code
     */
    private String ruleCode(CommissionLine line) {
        return line.ruleVersion == null ? "—" : line.ruleVersion.rule.code;
    }

    /**
     * The statement number of a line, or a dash.
     *
     * @param line the line
     * @return the number
     */
    private String statementNumber(CommissionLine line) {
        return line.statement == null ? "—" : line.statement.statementNumber;
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
     * A labelled amount.
     *
     * @param label  the label
     * @param amount the amount
     */
    public record Amount(String label, BigDecimal amount) {
    }

    /**
     * The dashboards of an entity.
     *
     * @param entity          the entity code
     * @param byNature        the commissions by nature
     * @param reprises        the clawback total
     * @param escomptesVerses the advances paid out
     * @param escomptesApures the advances offset
     * @param debtorAccounts  the number of debtor accounts
     * @param topDistributors the top distributors by commission
     * @param anomalies       the number of held events
     */
    public record Dashboard(String entity, List<Amount> byNature, BigDecimal reprises, BigDecimal escomptesVerses,
                            BigDecimal escomptesApures, long debtorAccounts, List<Amount> topDistributors,
                            long anomalies) {
    }

    /**
     * The operating indicators of an entity.
     *
     * @param entity      the entity code
     * @param activeRules the number of active rule versions
     * @param manualShare the share of manually-adjusted lines
     * @param clawback    the total of clawed-back amounts
     */
    public record Indicators(String entity, long activeRules, BigDecimal manualShare, BigDecimal clawback) {
    }

    /**
     * A consolidated entity summary.
     *
     * @param code  the entity code
     * @param name  the entity name
     * @param total the commission total
     */
    public record EntitySummary(String code, String name, BigDecimal total) {
    }

    /**
     * One cross-search row, carrying the FIN-07 chain references.
     *
     * @param distributor     the distributor code
     * @param product         the product code
     * @param nature          the line nature
     * @param basis           the basis amount
     * @param rate            the rate
     * @param amount          the amount
     * @param rule            the rule code
     * @param statementNumber the bordereau number
     * @param month           the period month
     */
    public record SearchRow(String distributor, String product, String nature, BigDecimal basis, BigDecimal rate,
                            BigDecimal amount, String rule, String statementNumber, LocalDate month) {
    }

    /**
     * The margin of a product.
     *
     * @param product  the product code
     * @param received the commission received (inbound)
     * @param paid     the commission paid (outbound)
     * @param margin   the margin (received − paid)
     */
    public record MarginRow(String product, BigDecimal received, BigDecimal paid, BigDecimal margin) {
    }
}
