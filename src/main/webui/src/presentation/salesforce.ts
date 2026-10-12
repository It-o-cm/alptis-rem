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
