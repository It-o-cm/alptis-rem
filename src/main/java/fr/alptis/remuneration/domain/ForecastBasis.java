package fr.alptis.remuneration.domain;

/**
 * How the provisional (forecast) commission line of a product is built at subscription (section 5.7, P3).
 */
public enum ForecastBasis {
    /** Forecast on the issued premiums of the coverage. */
    ISSUED_PREMIUMS,
    /** Forecast once at subscription on the annualised premium. */
    ANNUALISED_AT_SUBSCRIPTION,
    /** No forecast line for this product; the earning directly debits the commission expense. */
    NONE
}
