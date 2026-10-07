package fr.alptis.remuneration.domain;

/** How the forecast (provisional) line of a product is built (section 5.7). */
public enum ForecastBasis {
    ISSUED_PREMIUMS,
    ANNUALISED_AT_SUBSCRIPTION,
    NONE
}
