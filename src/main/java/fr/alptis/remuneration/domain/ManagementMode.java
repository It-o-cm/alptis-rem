package fr.alptis.remuneration.domain;

/**
 * How a product's contracts are managed (section 5.1), which drives how its data reaches the application.
 */
public enum ManagementMode {
    /** Managed internally by a group tool (CLEVA, RSI, Kelia). */
    DIRECT,
    /** Managed by a delegated manager who sends production, collection or commission files. */
    DELEGATED
}
