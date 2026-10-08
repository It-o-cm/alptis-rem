package fr.alptis.remuneration.domain;

/**
 * How a tiered scale is read (section 5.4).
 */
public enum ScaleMode {
    /** Each tier's rate applies only to the part of the measure within that tier. */
    MARGINAL,
    /** Once a tier is reached, its rate applies to the whole measure. */
    WHOLE
}
