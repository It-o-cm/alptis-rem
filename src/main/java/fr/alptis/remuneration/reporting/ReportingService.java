package fr.alptis.remuneration.reporting;

import fr.alptis.remuneration.accounts.AccountService;
import fr.alptis.remuneration.domain.BusinessEvent;
import fr.alptis.remuneration.domain.CommissionLine;
import fr.alptis.remuneration.domain.Coverage;
import fr.alptis.remuneration.domain.Direction;
import fr.alptis.remuneration.domain.Distributor;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Reporting read model (section 8.12): the dashboards over a month range with the month, nature,
 * gamme and entity axes and all their rubrics (REP-01), the filtered cross-search (REP-02/03), the
 * consolidated group view by entity and month (REP-07), the margin by product, insurer and
 * distributor (REP-08) and the four operating indicators month by month (REP-09). It only aggregates
 * already-decided lines, accounts and events — it decides and writes nothing, and every query is
 * entity-partitioned by the caller's scope (enforced in the resource).
 */
@ApplicationScoped
public class ReportingService {

    /** The statuses that count as commission (acquired, payable, paid). */
    private static final List<LineStatus> COUNTED = List.of(LineStatus.EARNED, LineStatus.PAYABLE,
            LineStatus.PAID);

    /** The default number of most-recent months shown when no range is given. */
    private static final int DEFAULT_MONTHS = 6;

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
     * Builds the dashboards of an entity over a month range (REP-01). When no range is given, the last
     * {@value #DEFAULT_MONTHS} months that carry data are used. The lines are filtered by product,
     * distributor, distributor group, sales delegation and nature (REP-03). Every rubric is returned
     * ready to both tabulate and chart.
     *
     * @param entityCode  the entity code
     * @param from        the first month (inclusive), or null for the default range
     * @param to          the last month (inclusive), or null for the default range
     * @param filter      the line filter (product, distributor, group, delegation, nature)
     * @return the dashboard view
     */
    public Dashboard dashboard(String entityCode, LocalDate from, LocalDate to, LineFilter filter) {
        LegalEntity entity = entity(entityCode);
        List<LocalDate> months = resolveRange(entity, from, to);
        LocalDate start = months.isEmpty() ? null : months.get(0);
        LocalDate end = months.isEmpty() ? null : months.get(months.size() - 1);

        Map<String, BigDecimal> byNature = new LinkedHashMap<>();
        Map<String, BigDecimal> byGamme = new LinkedHashMap<>();
        Map<String, BigDecimal> byDistributor = new LinkedHashMap<>();
        Map<String, BigDecimal> byMonthNature = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> reprisesByMonth = emptyMonths(months);
        Map<LocalDate, BigDecimal> versesByMonth = emptyMonths(months);
        for (CommissionLine line : countedLines(entity, start, end)) {
            if (!matches(line, filter)) {
                continue;
            }
            byNature.merge(line.nature.name(), line.amount, BigDecimal::add);
            byGamme.merge(gamme(line), line.amount, BigDecimal::add);
            byDistributor.merge(distributorCode(line), line.amount, BigDecimal::add);
            LocalDate month = monthOf(line);
            if (month != null) {
                byMonthNature.merge(month + "|" + line.nature.name(), line.amount, BigDecimal::add);
                if (line.nature == LineNature.CLAWBACK) {
                    reprisesByMonth.merge(month, line.amount, BigDecimal::add);
                } else if (line.nature == LineNature.ADVANCE) {
                    versesByMonth.merge(month, line.amount, BigDecimal::add);
                }
            }
        }
        Map<LocalDate, BigDecimal> apuresByMonth = offsetByMonth(entity, months, filter);

        List<MonthValue> escompte = new ArrayList<>();
        BigDecimal creance = BigDecimal.ZERO;
        for (LocalDate month : months) {
            BigDecimal verses = versesByMonth.get(month);
            BigDecimal apures = apuresByMonth.get(month);
            creance = creance.add(verses).subtract(apures);
            escompte.add(new MonthValue(month, verses, apures, creance));
        }
        List<Amount> top = amounts(byDistributor).stream()
                .sorted(Comparator.comparing((Amount a) -> a.amount().abs()).reversed())
                .limit(5).toList();
        long debtors = accounts.debtors(entity).size();
        long anomalies = BusinessEvent.count("entity = ?1 and processingStatus = ?2", entity,
                ProcessingStatus.ON_HOLD);
        return new Dashboard(entityCode, start, end, months, amounts(byNature), amounts(byGamme),
                monthNatures(byMonthNature), monthAmounts(reprisesByMonth, months), escompte, debtors, top,
                anomalies);
    }

    /**
     * Builds the operating indicators of an entity month by month over a range (REP-09): the active
     * rules, the cycle duration in days, the share of manually-adjusted lines and the clawed-back
     * amounts.
     *
     * @param entityCode the entity code
     * @param from       the first month (inclusive), or null for the default range
     * @param to         the last month (inclusive), or null for the default range
     * @return the indicators view
     */
    public Indicators indicators(String entityCode, LocalDate from, LocalDate to) {
        LegalEntity entity = entity(entityCode);
        List<LocalDate> months = resolveRange(entity, from, to);
        List<IndicatorMonth> rows = new ArrayList<>();
        for (LocalDate month : months) {
            long activeRules = RuleVersion.count(
                    "rule.plan.entity = ?1 and status = ?2 and (validFrom is null or validFrom <= ?3)"
                            + " and (validTo is null or validTo >= ?4)",
                    entity, VersionStatus.ACTIVE, endOfMonth(month), month);
            long total = CommissionLine.count("entity = ?1 and period.periodMonth = ?2", entity, month);
            long manual = CommissionLine.count("entity = ?1 and period.periodMonth = ?2 and nature = ?3", entity,
                    month, LineNature.MANUAL);
            BigDecimal manualShare = total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(manual).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
            rows.add(new IndicatorMonth(month, activeRules, cycleDuration(entity, month), manualShare,
                    clawback(entity, month)));
        }
        return new Indicators(entityCode, months, rows);
    }

    /**
     * Builds the consolidated group view: the commission total of every entity, month by month over a
     * range (REP-07). Reserved to cross-entity users; the caller's scope is checked by the resource.
     *
     * @param from the first month (inclusive), or null for the default range
     * @param to   the last month (inclusive), or null for the default range
     * @return the consolidated view
     */
    public Consolidated consolidated(LocalDate from, LocalDate to) {
        TreeSet<LocalDate> months = new TreeSet<>();
        List<EntitySummary> summaries = new ArrayList<>();
        List<EntityMonth> cells = new ArrayList<>();
        for (LegalEntity entity : LegalEntity.<LegalEntity>listAll()) {
            List<LocalDate> range = resolveRange(entity, from, to);
            months.addAll(range);
            Map<LocalDate, BigDecimal> totals = emptyMonths(range);
            BigDecimal total = BigDecimal.ZERO;
            // The lines are range-bounded, so every one carries a month already present in the totals.
            for (CommissionLine line : countedLines(entity, first(range), last(range))) {
                totals.merge(monthOf(line), line.amount, BigDecimal::add);
                total = total.add(line.amount);
            }
            summaries.add(new EntitySummary(entity.code, entity.name, total));
            for (LocalDate month : range) {
                cells.add(new EntityMonth(entity.code, month, totals.get(month)));
            }
        }
        return new Consolidated(new ArrayList<>(months), summaries, cells);
    }

    /**
     * Answers "paid to whom, when, why": the commission lines of an entity filtered by product,
     * distributor, distributor group, sales delegation, nature and month, each carrying its rule and
     * bordereau references (REP-02/03, FIN-07).
     *
     * @param entityCode the entity code
     * @param filter     the line filter
     * @param month      the period month filter, or null
     * @return the matching rows
     */
    public List<SearchRow> search(String entityCode, LineFilter filter, LocalDate month) {
        LegalEntity entity = entity(entityCode);
        List<SearchRow> rows = new ArrayList<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list("entity = ?1 order by id", entity)) {
            if (!COUNTED.contains(line.status)
                    || (month != null && (line.period == null || !line.period.periodMonth.equals(month)))
                    || !matches(line, filter)) {
                continue;
            }
            rows.add(new SearchRow(distributorCode(line), productCode(line), gamme(line), line.nature.name(),
                    line.basisAmount, line.rate, line.amount, ruleCode(line), statementNumber(line),
                    monthOf(line)));
        }
        return rows;
    }

    /**
     * Builds the margin by product, insurer and distributor (REP-08): the commission received (inbound)
     * minus the commission paid (outbound) grouped on each of the three axes.
     *
     * @param entityCode the entity code
     * @return the margin view
     */
    public Margin margin(String entityCode) {
        LegalEntity entity = entity(entityCode);
        Map<String, BigDecimal[]> byProduct = new LinkedHashMap<>();
        Map<String, BigDecimal[]> byInsurer = new LinkedHashMap<>();
        Map<String, BigDecimal[]> byDistributor = new LinkedHashMap<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list("entity = ?1 and status in ?2", entity,
                COUNTED)) {
            boolean inbound = line.direction == Direction.INBOUND;
            accrue(byProduct, productCode(line), line.amount, inbound);
            accrue(byInsurer, insurerCode(line), line.amount, inbound);
            accrue(byDistributor, distributorCode(line), line.amount, inbound);
        }
        return new Margin(marginRows(byProduct), marginRows(byInsurer), marginRows(byDistributor));
    }

    /**
     * Exports a reporting list as a semicolon-separated CSV at row grain (REP-04). The same text opens
     * as a spreadsheet; dev introduces no spreadsheet library (as for the bordereaux). Every object is
     * entity-partitioned by the caller's scope (enforced in the resource).
     *
     * @param entityCode the entity code (ignored for the consolidated object)
     * @param object     the list to export: SEARCH, MARGIN, INDICATORS, DASHBOARD or CONSOLIDATED
     * @param from       the first month, or null
     * @param to         the last month, or null
     * @param filter     the line filter
     * @param month      the search month filter, or null
     * @return the CSV text
     */
    public String exportCsv(String entityCode, String object, LocalDate from, LocalDate to, LineFilter filter,
                            LocalDate month) {
        StringBuilder csv = new StringBuilder();
        switch (object) {
            case "SEARCH" -> {
                csv.append("distributor;product;gamme;nature;basis;rate;amount;rule;statement;month\n");
                for (SearchRow r : search(entityCode, filter, month)) {
                    row(csv, r.distributor(), r.product(), r.gamme(), r.nature(), num(r.basis()), num(r.rate()),
                            num(r.amount()), r.rule(), r.statementNumber(), text(r.month()));
                }
            }
            case "MARGIN" -> {
                csv.append("axis;key;received;paid;margin\n");
                Margin margin = margin(entityCode);
                marginCsv(csv, "PRODUCT", margin.byProduct());
                marginCsv(csv, "INSURER", margin.byInsurer());
                marginCsv(csv, "DISTRIBUTOR", margin.byDistributor());
            }
            case "INDICATORS" -> {
                csv.append("month;active_rules;cycle_duration_days;manual_share;clawback\n");
                for (IndicatorMonth r : indicators(entityCode, from, to).rows()) {
                    row(csv, text(r.month()), String.valueOf(r.activeRules()), num(r.cycleDuration()),
                            num(r.manualShare()), num(r.clawback()));
                }
            }
            case "DASHBOARD" -> {
                csv.append("month;nature;amount\n");
                for (MonthNature r : dashboard(entityCode, from, to, filter).byMonthNature()) {
                    row(csv, text(r.month()), r.nature(), num(r.amount()));
                }
            }
            case "CONSOLIDATED" -> {
                csv.append("entity;month;total\n");
                for (EntityMonth r : consolidated(from, to).cells()) {
                    row(csv, r.entity(), text(r.month()), num(r.total()));
                }
            }
            default -> throw new fr.alptis.remuneration.domain.ValidationException(
                    "Objet d'export inconnu : " + object);
        }
        return csv.toString();
    }

    /**
     * Appends one CSV margin row per key.
     *
     * @param csv  the builder
     * @param axis the axis label
     * @param rows the margin rows
     */
    private static void marginCsv(StringBuilder csv, String axis, List<MarginRow> rows) {
        for (MarginRow r : rows) {
            row(csv, axis, r.key(), num(r.received()), num(r.paid()), num(r.margin()));
        }
    }

    /**
     * Appends a semicolon-separated CSV row.
     *
     * @param csv    the builder
     * @param values the cell values
     */
    private static void row(StringBuilder csv, String... values) {
        csv.append(String.join(";", values)).append('\n');
    }

    /**
     * Formats a nullable amount as a plain decimal, or an empty cell.
     *
     * @param value the amount, or null
     * @return the plain string, or empty
     */
    private static String num(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /**
     * Formats a nullable date, or an empty cell.
     *
     * @param value the date, or null
     * @return the ISO string, or empty
     */
    private static String text(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Resolves a month range: the given bounds, or the last {@value #DEFAULT_MONTHS} months carrying
     * data when no bound is given. A single given bound anchors the other.
     *
     * @param entity the entity
     * @param from   the first month, or null
     * @param to     the last month, or null
     * @return the months of the range, ascending (possibly empty)
     */
    private List<LocalDate> resolveRange(LegalEntity entity, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return monthsBetween(from, to);
        }
        TreeSet<LocalDate> withData = new TreeSet<>();
        for (CommissionLine line : CommissionLine.<CommissionLine>list("entity = ?1 and status in ?2", entity,
                COUNTED)) {
            LocalDate month = monthOf(line);
            if (month != null) {
                withData.add(month);
            }
        }
        if (withData.isEmpty()) {
            return List.of();
        }
        List<LocalDate> all = new ArrayList<>(withData);
        LocalDate lo = from != null ? from : all.get(Math.max(0, all.size() - DEFAULT_MONTHS));
        LocalDate hi = to != null ? to : all.get(all.size() - 1);
        return monthsBetween(lo, hi);
    }

    /**
     * The inclusive list of first-of-month dates between two months.
     *
     * @param from the first month
     * @param to   the last month
     * @return the months, ascending (empty when from is after to)
     */
    private static List<LocalDate> monthsBetween(LocalDate from, LocalDate to) {
        LocalDate lo = from.withDayOfMonth(1);
        LocalDate hi = to.withDayOfMonth(1);
        List<LocalDate> months = new ArrayList<>();
        while (!lo.isAfter(hi)) {
            months.add(lo);
            lo = lo.plusMonths(1);
        }
        return months;
    }

    /**
     * Tells whether a line passes the product, distributor, group, delegation and nature filters.
     *
     * @param line   the line
     * @param filter the filter
     * @return {@code true} when the line passes every set filter
     */
    private boolean matches(CommissionLine line, LineFilter filter) {
        if (filter.nature() != null && line.nature != filter.nature()) {
            return false;
        }
        if (filter.product() != null && !filter.product().equals(productCode(line))) {
            return false;
        }
        Distributor distributor = line.originDistributor;
        if (filter.distributor() != null && !filter.distributor().equals(distributorCode(line))) {
            return false;
        }
        if (filter.group() != null
                && (distributor == null || distributor.group == null
                        || !filter.group().equals(distributor.group.code))) {
            return false;
        }
        return filter.delegation() == null
                || (distributor != null && filter.delegation().equals(distributor.salesDelegation));
    }

    /**
     * The counted lines of an entity within a month range (inclusive), or all when the range is empty.
     *
     * @param entity the entity
     * @param start  the first month, or null
     * @param end    the last month, or null
     * @return the counted lines
     */
    private List<CommissionLine> countedLines(LegalEntity entity, LocalDate start, LocalDate end) {
        // start and end are always set together (both the bounds of a non-empty range) or both null.
        if (start == null) {
            return CommissionLine.list("entity = ?1 and status in ?2", entity, COUNTED);
        }
        return CommissionLine.list(
                "entity = ?1 and status in ?2 and period.periodMonth >= ?3 and period.periodMonth <= ?4",
                entity, COUNTED, start, end);
    }

    /**
     * The offset (apurement) totals by month over a range, honouring the filter.
     *
     * @param entity the entity
     * @param months the months of the range
     * @param filter the line filter
     * @return the offset total by month
     */
    private Map<LocalDate, BigDecimal> offsetByMonth(LegalEntity entity, List<LocalDate> months,
                                                     LineFilter filter) {
        Map<LocalDate, BigDecimal> byMonth = emptyMonths(months);
        for (CommissionLine line : CommissionLine.<CommissionLine>list("entity = ?1 and status = ?2", entity,
                LineStatus.OFFSET)) {
            // containsKey is false for a line outside the range (and for a null month: no null key).
            LocalDate month = monthOf(line);
            if (byMonth.containsKey(month) && matches(line, filter)) {
                byMonth.merge(month, line.amount, BigDecimal::add);
            }
        }
        return byMonth;
    }

    /**
     * The clawed-back total of an entity for a month.
     *
     * @param entity the entity
     * @param month  the month
     * @return the clawback total
     */
    private BigDecimal clawback(LegalEntity entity, LocalDate month) {
        BigDecimal total = BigDecimal.ZERO;
        for (CommissionLine line : CommissionLine.<CommissionLine>list(
                "entity = ?1 and period.periodMonth = ?2 and nature = ?3 and status in ?4", entity, month,
                LineNature.CLAWBACK, COUNTED)) {
            total = total.add(line.amount);
        }
        return total;
    }

    /**
     * The cycle duration of an entity's month: the days between the earliest earned line and the latest
     * paid line of that month, or zero when nothing is paid yet.
     *
     * @param entity the entity
     * @param month  the month
     * @return the cycle duration in days
     */
    private BigDecimal cycleDuration(LegalEntity entity, LocalDate month) {
        java.time.OffsetDateTime earned = null;
        java.time.OffsetDateTime paid = null;
        for (CommissionLine line : CommissionLine.<CommissionLine>list("entity = ?1 and period.periodMonth = ?2",
                entity, month)) {
            if (line.earnedAt != null && (earned == null || line.earnedAt.isBefore(earned))) {
                earned = line.earnedAt;
            }
            if (line.paidAt != null && (paid == null || line.paidAt.isAfter(paid))) {
                paid = line.paidAt;
            }
        }
        if (earned == null || paid == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(earned, paid));
    }

    /**
     * Accrues a line's amount into the inbound or outbound column of a key's margin accumulator.
     *
     * @param map     the accumulator
     * @param key     the grouping key
     * @param amount  the line amount
     * @param inbound whether the line is inbound (received)
     */
    private static void accrue(Map<String, BigDecimal[]> map, String key, BigDecimal amount, boolean inbound) {
        BigDecimal[] totals = map.computeIfAbsent(key, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        if (inbound) {
            totals[0] = totals[0].add(amount);
        } else {
            totals[1] = totals[1].add(amount);
        }
    }

    /**
     * Converts a margin accumulator to margin rows (received, paid, received − paid).
     *
     * @param map the accumulator
     * @return the margin rows
     */
    private static List<MarginRow> marginRows(Map<String, BigDecimal[]> map) {
        List<MarginRow> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : map.entrySet()) {
            BigDecimal[] totals = entry.getValue();
            rows.add(new MarginRow(entry.getKey(), totals[0], totals[1], totals[0].subtract(totals[1])));
        }
        return rows;
    }

    /**
     * A mutable month→zero map preserving the range order.
     *
     * @param months the months
     * @return the zero-initialised map
     */
    private static Map<LocalDate, BigDecimal> emptyMonths(List<LocalDate> months) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        for (LocalDate month : months) {
            map.put(month, BigDecimal.ZERO);
        }
        return map;
    }

    /**
     * Converts an amount map to a list of amount rows in insertion order.
     *
     * @param map the map
     * @return the amount rows
     */
    private static List<Amount> amounts(Map<String, BigDecimal> map) {
        List<Amount> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : map.entrySet()) {
            list.add(new Amount(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    /**
     * Converts a month→amount map to month-value rows in range order.
     *
     * @param map    the map
     * @param months the range order
     * @return the month amounts
     */
    private static List<MonthValue> monthAmounts(Map<LocalDate, BigDecimal> map, List<LocalDate> months) {
        List<MonthValue> list = new ArrayList<>();
        for (LocalDate month : months) {
            list.add(new MonthValue(month, map.get(month), null, null));
        }
        return list;
    }

    /**
     * Converts a {@code month|nature}→amount map to month-nature rows.
     *
     * @param map the map
     * @return the month-nature rows
     */
    private static List<MonthNature> monthNatures(Map<String, BigDecimal> map) {
        List<MonthNature> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : map.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            list.add(new MonthNature(LocalDate.parse(parts[0]), parts[1], entry.getValue()));
        }
        return list;
    }

    /**
     * The month of a line, or null when it carries no period.
     *
     * @param line the line
     * @return the month, or null
     */
    private static LocalDate monthOf(CommissionLine line) {
        return line.period == null ? null : line.period.periodMonth;
    }

    /**
     * The last day of a month.
     *
     * @param month the first day of the month
     * @return the last day of the month
     */
    private static LocalDate endOfMonth(LocalDate month) {
        return month.withDayOfMonth(month.lengthOfMonth());
    }

    /**
     * The first month of a range, or null when empty.
     *
     * @param range the range
     * @return the first month, or null
     */
    private static LocalDate first(List<LocalDate> range) {
        return range.isEmpty() ? null : range.get(0);
    }

    /**
     * The last month of a range, or null when empty.
     *
     * @param range the range
     * @return the last month, or null
     */
    private static LocalDate last(List<LocalDate> range) {
        return range.isEmpty() ? null : range.get(range.size() - 1);
    }

    /**
     * The distributor partner code of a line, or a dash.
     *
     * @param line the line
     * @return the code
     */
    private static String distributorCode(CommissionLine line) {
        return line.originDistributor == null ? "—" : line.originDistributor.partnerCode;
    }

    /**
     * The product code of a line, reached through the coverage option, or a dash.
     *
     * @param line the line
     * @return the code
     */
    private static String productCode(CommissionLine line) {
        Product product = productOf(line);
        return product == null ? "—" : product.code;
    }

    /**
     * The gamme (range) label of a line's product, or a dash.
     *
     * @param line the line
     * @return the gamme label
     */
    private static String gamme(CommissionLine line) {
        Product product = productOf(line);
        if (product == null || product.range == null) {
            return "—";
        }
        return product.range.label;
    }

    /**
     * The insurer code of a line's product, or a dash.
     *
     * @param line the line
     * @return the insurer code
     */
    private static String insurerCode(CommissionLine line) {
        Product product = productOf(line);
        if (product == null || product.insurer == null) {
            return "—";
        }
        return product.insurer.code;
    }

    /**
     * The product behind a line, through the coverage option, or null.
     *
     * @param line the line
     * @return the product, or null
     */
    private static Product productOf(CommissionLine line) {
        Coverage coverage = line.coverage;
        if (coverage == null || coverage.option == null) {
            return null;
        }
        ProductOption option = coverage.option;
        return option.product;
    }

    /**
     * The rule code of a line, or a dash.
     *
     * @param line the line
     * @return the code
     */
    private static String ruleCode(CommissionLine line) {
        return line.ruleVersion == null ? "—" : line.ruleVersion.rule.code;
    }

    /**
     * The statement number of a line, or a dash.
     *
     * @param line the line
     * @return the number
     */
    private static String statementNumber(CommissionLine line) {
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
     * The filters of the dashboards and the cross-search (REP-03). A null field means no filter.
     *
     * @param product     the product code filter, or null
     * @param distributor the distributor partner-code filter, or null
     * @param group       the distributor group-code filter, or null
     * @param delegation  the sales-delegation filter, or null
     * @param nature      the nature filter, or null
     */
    public record LineFilter(String product, String distributor, String group, String delegation,
                             LineNature nature) {

        /**
         * A filter that keeps every line.
         *
         * @return the empty filter
         */
        public static LineFilter none() {
            return new LineFilter(null, null, null, null, null);
        }
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
     * A month and the commission amount of a nature in it.
     *
     * @param month  the month
     * @param nature the nature
     * @param amount the amount
     */
    public record MonthNature(LocalDate month, String nature, BigDecimal amount) {
    }

    /**
     * A month and up to three values (used for the reprises series and the escompte series).
     *
     * @param month   the month
     * @param primary the primary value (reprises, or escomptes versés)
     * @param second  the second value (escomptes apurés), or null
     * @param third   the third value (créance restante), or null
     */
    public record MonthValue(LocalDate month, BigDecimal primary, BigDecimal second, BigDecimal third) {
    }

    /**
     * The dashboards of an entity over a month range.
     *
     * @param entity          the entity code
     * @param from            the first month of the range, or null when empty
     * @param to              the last month of the range, or null when empty
     * @param months          the months of the range
     * @param byNature        the commissions by nature over the range
     * @param byGamme         the commissions by gamme over the range
     * @param byMonthNature   the commissions by month and nature (stacked bars)
     * @param reprisesByMonth the clawbacks by month
     * @param escompteByMonth the advances paid, offset and outstanding by month
     * @param debtorAccounts  the number of debtor accounts
     * @param topDistributors the top distributors by commission
     * @param anomalies       the number of held events
     */
    public record Dashboard(String entity, LocalDate from, LocalDate to, List<LocalDate> months,
                            List<Amount> byNature, List<Amount> byGamme, List<MonthNature> byMonthNature,
                            List<MonthValue> reprisesByMonth, List<MonthValue> escompteByMonth,
                            long debtorAccounts, List<Amount> topDistributors, long anomalies) {
    }

    /**
     * The operating indicators of an entity month by month.
     *
     * @param entity the entity code
     * @param months the months of the range
     * @param rows   the indicators per month
     */
    public record Indicators(String entity, List<LocalDate> months, List<IndicatorMonth> rows) {
    }

    /**
     * The four operating indicators of one month.
     *
     * @param month         the month
     * @param activeRules   the number of active rule versions in force that month
     * @param cycleDuration the cycle duration in days
     * @param manualShare   the share of manually-adjusted lines
     * @param clawback      the clawed-back amount
     */
    public record IndicatorMonth(LocalDate month, long activeRules, BigDecimal cycleDuration,
                                 BigDecimal manualShare, BigDecimal clawback) {
    }

    /**
     * The consolidated group view.
     *
     * @param months    the union of the entities' months
     * @param summaries the per-entity commission totals
     * @param cells     the per-entity, per-month commission totals (grouped bars)
     */
    public record Consolidated(List<LocalDate> months, List<EntitySummary> summaries, List<EntityMonth> cells) {
    }

    /**
     * A consolidated entity summary.
     *
     * @param code  the entity code
     * @param name  the entity name
     * @param total the commission total over the range
     */
    public record EntitySummary(String code, String name, BigDecimal total) {
    }

    /**
     * One entity's commission total for one month.
     *
     * @param entity the entity code
     * @param month  the month
     * @param total  the commission total
     */
    public record EntityMonth(String entity, LocalDate month, BigDecimal total) {
    }

    /**
     * One cross-search row, carrying the FIN-07 chain references.
     *
     * @param distributor     the distributor code
     * @param product         the product code
     * @param gamme           the gamme label
     * @param nature          the line nature
     * @param basis           the basis amount
     * @param rate            the rate
     * @param amount          the amount
     * @param rule            the rule code
     * @param statementNumber the bordereau number
     * @param month           the period month
     */
    public record SearchRow(String distributor, String product, String gamme, String nature, BigDecimal basis,
                            BigDecimal rate, BigDecimal amount, String rule, String statementNumber,
                            LocalDate month) {
    }

    /**
     * The margin by product, insurer and distributor.
     *
     * @param byProduct     the margin by product
     * @param byInsurer     the margin by insurer
     * @param byDistributor the margin by distributor
     */
    public record Margin(List<MarginRow> byProduct, List<MarginRow> byInsurer, List<MarginRow> byDistributor) {
    }

    /**
     * The margin of one key (product, insurer or distributor).
     *
     * @param key      the grouping key
     * @param received the commission received (inbound)
     * @param paid     the commission paid (outbound)
     * @param margin   the margin (received − paid)
     */
    public record MarginRow(String key, BigDecimal received, BigDecimal paid, BigDecimal margin) {
    }
}
