// Presentation helpers for the sales-force module (section 8.14, FDV): French labels for the job
// codes of the 2026 grid, shown wherever a post is displayed or picked.

/** French label of each job code, matching the 2026 grid (section 8.14). */
const JOB_LABELS: Record<string, string> = {
  CAC: "Conseiller d'affaires courtage",
  RB: 'Responsable de secteur courtage',
  DR: 'Directeur régional',
  RR: 'Responsable de région',
  DNV: 'Directeur national des ventes',
};

/** The job codes in grid order, for a select. */
export const JOB_CODES: readonly string[] = ['CAC', 'RB', 'DR', 'RR', 'DNV'];

/** Returns the French label of a job code, or the code itself when unknown or absent. */
export function jobCodeLabel(code: string | null): string {
  if (code === null) {
    return '—';
  }
  return JOB_LABELS[code] ?? code;
}

/** French label of each measure type received for the sales force (FDV-02). */
const MEASURE_LABELS: Record<string, string> = {
  ABSENCE: 'Absences (hors congés et RTT)',
  PRODUCTION: 'Production par marché',
  ACTIVITY: 'Activité commerciale (CRM)',
  MARGIN: 'Marge',
  SPECIFICATIONS_RECEIVED: 'Cahiers des charges reçus',
};

/** Returns the French label of a measure type, or the code itself when unknown or absent. */
export function measureTypeLabel(code: string | null): string {
  if (code === null) {
    return '—';
  }
  return MEASURE_LABELS[code] ?? code;
}

/** French label of each objective type (FDV-03). */
const OBJECTIVE_LABELS: Record<string, string> = {
  INDIVIDUAL_QUANTITATIVE: 'Individuel quantitatif',
  QUALITATIVE: 'Qualitatif',
  COLLECTIVE_QUANTITATIVE: 'Collectif quantitatif',
};

/** The objective types offered in a select, in a natural order. */
export const OBJECTIVE_TYPES: readonly string[] = [
  'INDIVIDUAL_QUANTITATIVE',
  'COLLECTIVE_QUANTITATIVE',
  'QUALITATIVE',
];

/** Returns the French label of an objective type, or the code itself when unknown or absent. */
export function objectiveTypeLabel(code: string | null): string {
  if (code === null) {
    return '—';
  }
  return OBJECTIVE_LABELS[code] ?? code;
}
