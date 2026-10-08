package fr.alptis.remuneration.domain;

/**
 * The nine application roles of specification section 3. A user's roles are deduced from its
 *  directory groups through the {@code role_mapping} table; they gate every action, and the
 *  separation-of-duties rules forbid some combinations on the same object.
 */
public enum Role {
    /** Pilotage et Gestion Courtiers: configures rules, scales, agreements and controls, launches calculations, and approves rule versions and adjustments created by another user. */
    BUSINESS_ADMIN,
    /** Gestionnaire: follows imports, handles rejections, enters manual adjustments, runs controls and prepares validation. */
    OPERATOR,
    /** Direction du Développement: validates the month's calculation and the adjustments above a threshold. */
    BUSINESS_VALIDATOR,
    /** Comptabilité et trésorerie: checks the reconciliation, validates the payment batch, triggers accounting extractions and closes the period. */
    FINANCE_CONTROLLER,
    /** Read access to the accounts, statements and dashboards of the user's scope. */
    VIEWER,
    /** Read-only access to everything, including the audit trail and rule versions. */
    AUDITOR,
    /** DSSI: manages the directory-group to role correspondence and supervises jobs and flows; has no right on amounts. */
    IT_ADMIN,
    /** Regional manager: validates the variable pay of the sales force team (section 8.14). */
    SALES_MANAGER,
    /** Salesperson: consults their own variable pay and history (section 8.14). */
    SALES_REP
}
