package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ImportBatch;
import fr.alptis.remuneration.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Entry point of the import engine: dispatches a received or uploaded file to the right pivot
 * importer according to its flow code (section 9.9). Mapped free files (section 9.4) are handled by
 * the mapping engine before reaching this facade.
 */
@ApplicationScoped
public class ImportService {

    private final EventImportService events;
    private final CoverageImportService coverages;
    private final ReferentialImportService referentials;
    private final ProductReferentialImportService products;
    private final AccountQualityImportService accountsAndQuality;
    private final TransferImportService transfers;
    private final PayeeExternalImportService payeeAndCodes;
    private final OpeningBalanceImportService openingBalances;
    private final EmployeeImportService employees;

    /**
     * Creates the facade.
     *
     * @param events             the events importer
     * @param coverages          the coverages importer
     * @param referentials       the distributor-hierarchy importer
     * @param products           the product-referential importer
     * @param accountsAndQuality the bank-account and quality-indicator importer
     * @param transfers          the portfolio-transfer and replacement-order importer
     * @param payeeAndCodes      the payment-rule and external-code importer
     */
    @Inject
    public ImportService(EventImportService events, CoverageImportService coverages,
                         ReferentialImportService referentials, ProductReferentialImportService products,
                         AccountQualityImportService accountsAndQuality, TransferImportService transfers,
                         PayeeExternalImportService payeeAndCodes, OpeningBalanceImportService openingBalances,
                         EmployeeImportService employees) {
        this.events = events;
        this.coverages = coverages;
        this.referentials = referentials;
        this.products = products;
        this.accountsAndQuality = accountsAndQuality;
        this.transfers = transfers;
        this.payeeAndCodes = payeeAndCodes;
        this.openingBalances = openingBalances;
        this.employees = employees;
    }

    /**
     * Imports a pivot file identified by its flow code.
     *
     * @param entityCode   the entity code
     * @param flowCode     the flow code (object name, section 9.9)
     * @param sourceSystem the source system
     * @param fileName     the file name
     * @param content      the raw content
     * @return the journalled batch
     * @throws ValidationException if the flow code is not a known pivot flow
     */
    public ImportBatch importFile(String entityCode, String flowCode, String sourceSystem, String fileName,
                                  String content) {
        return switch (flowCode) {
            case FlowCodes.EVENTS -> events.importEvents(entityCode, sourceSystem, fileName, content);
            case FlowCodes.COVERAGES -> coverages.importCoverages(entityCode, sourceSystem, fileName, content);
            case FlowCodes.DISTRIBUTORS -> referentials.importDistributors(entityCode, sourceSystem, fileName, content);
            case FlowCodes.DISTRIBUTOR_GROUPS -> referentials.importGroups(entityCode, sourceSystem, fileName, content);
            case FlowCodes.PRODUCTION_CODES -> referentials.importProductionCodes(entityCode, sourceSystem, fileName, content);
            case ProductReferentialImportService.INSURERS -> products.importInsurers(entityCode, sourceSystem, fileName, content);
            case ProductReferentialImportService.PRODUCT_RANGES -> products.importRanges(entityCode, sourceSystem, fileName, content);
            case ProductReferentialImportService.PRODUCTS -> products.importProducts(entityCode, sourceSystem, fileName, content);
            case ProductReferentialImportService.PRODUCT_OPTIONS -> products.importOptions(entityCode, sourceSystem, fileName, content);
            case FlowCodes.BANK_ACCOUNTS -> accountsAndQuality.importBankAccounts(entityCode, sourceSystem, fileName, content);
            case FlowCodes.QUALITY_INDICATORS -> accountsAndQuality.importQualityIndicators(entityCode, sourceSystem, fileName, content);
            case TransferImportService.PORTFOLIO_TRANSFERS -> transfers.importPortfolioTransfers(entityCode, sourceSystem, fileName, content);
            case TransferImportService.REPLACEMENT_ORDERS -> transfers.importReplacementOrders(entityCode, sourceSystem, fileName, content);
            case PayeeExternalImportService.PAYEE_RULES -> payeeAndCodes.importPayeeRules(entityCode, sourceSystem, fileName, content);
            case PayeeExternalImportService.EXTERNAL_CODES -> payeeAndCodes.importExternalCodes(entityCode, sourceSystem, fileName, content);
            case OpeningBalanceImportService.OPENING_BALANCES -> openingBalances.importOpeningBalances(entityCode, sourceSystem, fileName, content);
            case EmployeeImportService.EMPLOYEES -> employees.importEmployees(entityCode, sourceSystem, fileName, content);
            case EmployeeImportService.EMPLOYEE_MEASURES -> employees.importMeasures(entityCode, sourceSystem, fileName, content);
            default -> throw new ValidationException("Flux inconnu : " + flowCode);
        };
    }
}
