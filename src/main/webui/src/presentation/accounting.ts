// Presentation helpers for the accounting scheme and entries screens. The server decides each
// accounting event, scheme variant and account role; these maps only turn the stored codes into the
// French labels the operator reads (ADM-03, section 10.2), so no technical code shows on screen.

/** French labels of the accounting events (section 10.2). */
const ACCOUNTING_EVENT_LABELS: Record<string, string> = {
  FORECAST_RECOGNITION: 'Constatation du prévisionnel',
  FORECAST_ADJUSTMENT: 'Ajustement du prévisionnel',
  EARNING: 'Acquisition',
  PAYABLE_RECOGNITION: 'Constatation à payer',
  PAYMENT: 'Paiement',
  PAYMENT_REJECTION: 'Rejet de paiement',
  ADVANCE_GRANTED: 'Escompte accordé',
  ADVANCE_OFFSETTING: 'Apurement d’escompte',
  ADVANCE_SETTLEMENT: 'Régularisation d’escompte',
  ADVANCE_WRITE_OFF: 'Abandon de reliquat d’escompte',
  CLAWBACK: 'Reprise',
  SUPPLEMENT: 'Complément',
  ACCOUNT_TRANSFER: 'Compensation entre comptes',
  INSURER_RECEIVABLE: 'Créance assureur',
};

/** French labels of the accounting-scheme variants (section 10.2). */
const ACCOUNTING_VARIANT_LABELS: Record<string, string> = {
  STANDARD: 'Standard',
  NEGATIVE: 'Montant négatif',
  NO_FORECAST: 'Sans prévisionnel',
  SAVINGS: 'Épargne retraite',
};

/** French labels of the account roles (section 10.2). */
const ACCOUNT_ROLE_LABELS: Record<string, string> = {
  FORECAST_EXPENSE: 'Charge de commission prévisionnelle',
  PROVISION: 'Provision pour commissions à payer',
  EARNED_COMMISSIONS: 'Commissions acquises',
  DISTRIBUTOR_PAYABLE: 'Distributeur, commissions à payer',
  BANK: 'Banque',
  DISTRIBUTOR_ADVANCES: 'Avances aux distributeurs',
  COMMISSION_EXPENSE: 'Charge de commission',
  CLAWBACK_INCOME: 'Reprises de commissions',
  INSURER_RECEIVABLE: 'Assureur, commissions à recevoir',
  COMMISSION_INCOME: 'Produits de commissions',
};

/** French label of an accounting event, a dash for none, or the raw code when unknown. */
export function accountingEventLabel(event: string | null): string {
  if (event === null) return '—';
  return ACCOUNTING_EVENT_LABELS[event] ?? event;
}

/** French label of an accounting-scheme variant, a dash for none, or the raw code when unknown. */
export function accountingVariantLabel(variant: string | null): string {
  if (variant === null) return '—';
  return ACCOUNTING_VARIANT_LABELS[variant] ?? variant;
}

/** French label of an account role, a dash for none, or the raw code when unknown. */
export function accountRoleLabel(role: string | null): string {
  if (role === null) return '—';
  return ACCOUNT_ROLE_LABELS[role] ?? role;
}
