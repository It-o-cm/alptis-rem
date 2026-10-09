package fr.alptis.remuneration.integration;

/**
 * Flow codes identifying the kind of an imported file; each equals the object name of the file
 * (section 9.9). An import batch is journalled under its flow code.
 */
public final class FlowCodes {

    /** Coverages pivot file (section 9.2). */
    public static final String COVERAGES = "coverages";

    /** Business events pivot file (section 9.3). */
    public static final String EVENTS = "events";

    /** Distributors referential file (section 9.10). */
    public static final String DISTRIBUTORS = "distributors";

    /** Distributor groups referential file (section 9.10). */
    public static final String DISTRIBUTOR_GROUPS = "distributor_groups";

    /** Production codes referential file (section 9.10). */
    public static final String PRODUCTION_CODES = "production_codes";

    /** Bank accounts file (section 9.10). */
    public static final String BANK_ACCOUNTS = "bank_accounts";

    /** Quality indicators file (section 9.10). */
    public static final String QUALITY_INDICATORS = "quality_indicators";

    /** Opening-balance file for the history reprise (ING-11, section 14). */
    public static final String OPENING_BALANCES = "opening_balances";

    /** Prevents instantiation. */
    private FlowCodes() {
    }
}
