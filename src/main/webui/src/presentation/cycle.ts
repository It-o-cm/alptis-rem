// Presentation helpers for the cycle, controls, adjustments and account screens (step 9). The server
// decides every state and amount; these functions only label and classify already-decided values and
// tell which actions a role may attempt (the server stays the authority).

/** French labels of the period states (section 7.1). */
const STATE_LABELS: Record<string, string> = {
  OPEN: 'Ouverte',
  LOADED: 'Données gelées',
  CALCULATED: 'Calculée',
  CONTROLLED: 'Contrôlée',
  BUSINESS_VALIDATED: 'Validée métier',
  PAYMENT_PREPARED: 'Paiement préparé',
  FINANCE_VALIDATED: 'Validée Finance',
  PAID: 'Payée',
  CLOSED: 'Clôturée',
};

/** The forward successor of each period state, null at the end of the cycle. */
const NEXT_STATE: Record<string, string> = {
  OPEN: 'LOADED',
  LOADED: 'CALCULATED',
  CALCULATED: 'CONTROLLED',
  CONTROLLED: 'BUSINESS_VALIDATED',
  BUSINESS_VALIDATED: 'PAYMENT_PREPARED',
  PAYMENT_PREPARED: 'FINANCE_VALIDATED',
  FINANCE_VALIDATED: 'PAID',
  PAID: 'CLOSED',
};

/** The roles that may drive the cycle. */
const CYCLE_ROLES = ['OPERATOR', 'BUSINESS_ADMIN', 'BUSINESS_VALIDATOR', 'FINANCE_CONTROLLER'];

/** French label of a period state, or the raw value when unknown. */
export function periodStateLabel(state: string): string {
  return STATE_LABELS[state] ?? state;
}

/** The forward successor state of a period, or null when the cycle is over. */
export function nextState(state: string): string | null {
  return NEXT_STATE[state] ?? null;
}

/** Tells whether a user holding the given roles may drive a period transition. */
export function canDrivePeriod(roles: readonly string[]): boolean {
  return roles.some((role) => CYCLE_ROLES.includes(role));
}

/** Tells whether a user holding the given roles may decide on an anomaly or submit an adjustment. */
export function canOperateCycle(roles: readonly string[]): boolean {
  return roles.includes('OPERATOR') || roles.includes('BUSINESS_ADMIN');
}

/** Tells whether a user holding the given roles may validate a lot for Finance and record results. */
export function canValidateFinance(roles: readonly string[]): boolean {
  return roles.includes('FINANCE_CONTROLLER');
}

/** French label of a payment lot or order status. */
export function paymentStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    DRAFT: 'Brouillon',
    VALIDATED: 'Validé',
    ISSUED: 'Émis',
    SETTLED: 'Réglé',
    TO_VALIDATE: 'À valider',
    REJECTED: 'Rejeté',
    CANCELLED: 'Annulé',
    PUBLISHED: 'Publié',
    CORRECTED: 'Rectifié',
  };
  return labels[status] ?? status;
}

/** French label of a payment lot type (section 7.1). */
export function batchTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    CYCLE: 'Lot de cycle',
    SAVINGS_CYCLE: 'Lot épargne retraite',
    OFF_CYCLE: 'Lot hors cycle',
    CHEQUE: 'Lot de chèques',
  };
  return labels[type] ?? type;
}

/** Tag CSS class for an anomaly severity. */
export function severityTag(severity: string): string {
  if (severity === 'BLOCKING') return 'tag ko';
  if (severity === 'WARNING') return 'tag warn';
  return 'tag mute';
}

/** French label of a control decision. */
export function decisionLabel(decision: string): string {
  if (decision === 'FIXED') return 'Corrigée';
  if (decision === 'ACCEPTED') return 'Acceptée';
  if (decision === 'DEFERRED') return 'Reportée';
  return 'Ouverte';
}
