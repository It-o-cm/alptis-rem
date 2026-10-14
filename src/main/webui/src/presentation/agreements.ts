// Presentation helpers for agreements and reserved-rule enrollments. The server decides each
// agreement type, signature state and enrollment status; these maps only turn the stored codes into
// the French labels the operator reads (ADM-03), so no technical code shows on screen.

/** French labels of the agreement types (section 5.4 glossary). */
const AGREEMENT_TYPE_LABELS: Record<string, string> = {
  PARTNERSHIP: 'Convention de partenariat',
  RCD_AMENDMENT: 'Avenant RCD',
  RCP_AMENDMENT: 'Avenant RCP',
  ADVANCE_AGREEMENT: 'Convention d’escompte',
  INSURER_PROTOCOL: 'Protocole assureur',
  MANDATE: 'Mandat',
};

/** French labels of the agreement signature states (section 5.4). */
const SIGNATURE_STATUS_LABELS: Record<string, string> = {
  SIGNED: 'Signé',
  NOT_SIGNED: 'Non signé',
};

/** French labels of the reserved-rule enrollment statuses (section 5.4). */
const ENROLLMENT_STATUS_LABELS: Record<string, string> = {
  PENDING_SIGNATURE: 'En attente de signature',
  ACTIVE: 'Active',
  ENDED: 'Terminée',
};

/** French label of an agreement type, a dash for none, or the raw code when unknown. */
export function agreementTypeLabel(type: string | null): string {
  if (type === null) return '—';
  return AGREEMENT_TYPE_LABELS[type] ?? type;
}

/** French label of a signature state, a dash for none, or the raw code when unknown. */
export function signatureStatusLabel(status: string | null): string {
  if (status === null) return '—';
  return SIGNATURE_STATUS_LABELS[status] ?? status;
}

/** French label of an enrollment status, a dash for none, or the raw code when unknown. */
export function enrollmentStatusLabel(status: string | null): string {
  if (status === null) return '—';
  return ENROLLMENT_STATUS_LABELS[status] ?? status;
}
