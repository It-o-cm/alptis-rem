// Presentation helpers for the event search, detail and held-events screens. The server decides each
// event's status and hold reason; these maps only turn the stored codes into the French labels the
// operator reads (ADM-03), so no technical code shows on screen.

/** French labels of the processing statuses (section 5.3). */
const STATUS_LABELS: Record<string, string> = {
  PENDING: 'En attente de calcul',
  ON_HOLD: 'Mis à l’écart',
  PROCESSED: 'Traité',
  NOT_COMMISSIONABLE: 'Non commissionnable',
};

/** French labels of the hold reasons (section 8.2). */
const HOLD_REASON_LABELS: Record<string, string> = {
  UNKNOWN_REFERENCE: 'Référence inconnue',
  NO_RULE: 'Aucune règle applicable',
  RATE_OUT_OF_GRID: 'Taux hors grille',
  CONCURRENT_RULES: 'Règles concurrentes',
  EXTERNAL_MISMATCH: 'Écart avec la commission externe',
  OUT_OF_BOUNDS: 'Hors bornes',
  UNMATCHED_NEGATIVE: 'Négatif non rapproché',
};

/** French labels of the event types (section 5.3). */
const EVENT_TYPE_LABELS: Record<string, string> = {
  PREMIUM_ISSUED: 'Prime émise',
  PREMIUM_COLLECTED: 'Prime encaissée',
  PREMIUM_CANCELLED: 'Prime annulée',
  PREMIUM_EXEMPTED: 'Prime exonérée',
  SUBSCRIPTION: 'Souscription',
  ENDORSEMENT: 'Avenant',
  TERMINATION: 'Résiliation',
  WITHDRAWAL: 'Renonciation',
  NOT_PURSUED: 'Sans suite',
  DEATH: 'Décès',
  UNPAID_NOTICE: 'Avis d’impayé',
  LITIGATION_OPENED: 'Contentieux ouvert',
  REINSTATEMENT: 'Remise en vigueur',
  INITIAL_PAYMENT: 'Versement initial',
  FREE_PAYMENT: 'Versement libre',
  SCHEDULED_PAYMENT: 'Versement programmé',
  INBOUND_TRANSFER: 'Transfert entrant',
  ARBITRAGE: 'Arbitrage',
  OUTSTANDING_FEE: 'Frais sur encours',
  OUTSTANDING_BALANCE: 'Encours moyen',
  PLAN_SUSPENDED: 'Plan suspendu',
  PLAN_CHANGED: 'Plan modifié',
  SURRENDER: 'Rachat',
  OUTBOUND_TRANSFER: 'Transfert sortant',
  EARLY_REPAYMENT: 'Rachat anticipé',
  FILE_FEE_COLLECTED: 'Frais de dossier encaissés',
  PARTIAL_EARLY_REPAYMENT: 'Rachat anticipé partiel',
  INSURER_COMMISSION: 'Commission assureur reçue',
  EXTERNAL_COMMISSION: 'Commission externe',
};

/** French label of a processing status, or the raw code when unknown. */
export function eventStatusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

/** French label of a hold reason, or a dash when absent, or the raw code when unknown. */
export function holdReasonLabel(reason: string | null): string {
  if (reason === null) return '—';
  return HOLD_REASON_LABELS[reason] ?? reason;
}

/** French label of an event type, or the raw code when unknown. */
export function eventTypeLabel(type: string): string {
  return EVENT_TYPE_LABELS[type] ?? type;
}

/** The processing statuses offered as search filters, with their labels. */
export const STATUS_OPTIONS: ReadonlyArray<{ value: string; label: string }> =
  Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }));

/** The event types offered as search filters, with their labels. */
export const EVENT_TYPE_OPTIONS: ReadonlyArray<{ value: string; label: string }> =
  Object.entries(EVENT_TYPE_LABELS).map(([value, label]) => ({ value, label }));
