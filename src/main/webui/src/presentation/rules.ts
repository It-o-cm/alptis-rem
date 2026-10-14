// Presentation helpers for the rule-configuration screens (PAR-01 to PAR-12).

/** French label of a calculation family, matching the Familles.dc.html mock-up. */
const FAMILY_LABELS: Record<string, string> = {
  LINEAR: 'Linéaire',
  UPFRONT: 'Précomptée',
  ADVANCE: 'Escompte',
  TIERED: 'Barème sur cumul',
  PORTFOLIO: 'Portefeuille sous conditions',
  SHARE: "Quote-part d'une autre commission",
  OUTSTANDING: 'Encours',
  FIXED: 'Montant fixe',
  EXTERNAL: 'Commission externe',
};

/** Returns the French label of a family code, or the code itself when unknown. */
export function familyLabel(code: string | null): string {
  if (code === null) {
    return '—';
  }
  return FAMILY_LABELS[code] ?? code;
}

/** French labels of the rule-version statuses (section 5.4). */
const VERSION_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Brouillon',
  APPROVED: 'Approuvée',
  ACTIVE: 'En vigueur',
  RETIRED: 'Retirée',
};

/** French label of a rule-version status, a dash for none, or the raw code when unknown. */
export function versionStatusLabel(status: string | null): string {
  if (status === null) return '—';
  return VERSION_STATUS_LABELS[status] ?? status;
}

/** CSS tag class for a version status, so the list reads at a glance. */
export function versionStatusClass(status: string | null): string {
  switch (status) {
    case 'ACTIVE':
      return 'tag ok';
    case 'APPROVED':
      return 'tag info';
    case 'RETIRED':
      return 'tag mute';
    default:
      return 'tag';
  }
}

/** One field derived from a family JSON Schema, for the generated form. */
export interface SchemaField {
  /** Property name. */
  readonly name: string;
  /** JSON Schema type (object, array, number, integer, string, boolean). */
  readonly type: string;
  /** Whether the property is required. */
  readonly required: boolean;
}

/**
 * Extracts the top-level fields of a family JSON Schema, in declaration order, so the interface can
 * build the family form (PAR-02). A malformed or property-less schema yields an empty list.
 */
export function schemaFields(schemaJson: string): SchemaField[] {
  let schema: unknown;
  try {
    schema = JSON.parse(schemaJson);
  } catch {
    return [];
  }
  if (typeof schema !== 'object' || schema === null) {
    return [];
  }
  const properties = (schema as { properties?: Record<string, { type?: string }> }).properties;
  if (!properties) {
    return [];
  }
  const required = new Set((schema as { required?: string[] }).required ?? []);
  return Object.entries(properties).map(([name, def]) => ({
    name,
    type: def.type ?? 'string',
    required: required.has(name),
  }));
}

/** Formats a conflict list for display: a dash when empty, else the joined codes. */
export function conflictLabel(conflicts: readonly string[]): string {
  return conflicts.length === 0 ? 'Aucun conflit' : conflicts.join(', ');
}

/** A payment condition carried by a rule version (PAR-07), as served to the interface. */
export interface ConditionView {
  /** Quality indicator type, when the condition reads an indicator. */
  readonly indicatorType: string | null;
  /** Counter code, when the condition reads a counter. */
  readonly counterCode: string | null;
  /** Comparison operator (LT, LE, GT, GE, EQ). */
  readonly operator: string | null;
  /** Threshold the measure is compared to. */
  readonly threshold: number | null;
  /** Behaviour on failure (FORFEIT, DEFER). */
  readonly onFailure: string | null;
}

/** French label of a failure behaviour, defaulting to the raw code. */
const FAILURE_LABELS: Record<string, string> = {
  FORFEIT: 'Perte',
  DEFER: 'Report',
};

/**
 * Summarises a version's reprise policy and payment schedule (PAR-08) for the versions table: a dash
 * when neither is set, else a compact description of what is set.
 */
export function repriseLabel(clawbackPolicy: string | null, paymentSchedule: string | null): string {
  const parts: string[] = [];
  if (clawbackPolicy) {
    parts.push(`Reprise ${clawbackPolicy}`);
  }
  if (paymentSchedule) {
    parts.push(`Échéancier ${paymentSchedule}`);
  }
  return parts.length === 0 ? '—' : parts.join(' · ');
}

/**
 * Summarises a version's payment conditions (PAR-07) for the versions table: a dash when none, else
 * one short clause per condition (measure, operator, threshold, failure behaviour).
 */
export function conditionsLabel(conditions: readonly ConditionView[]): string {
  if (conditions.length === 0) {
    return '—';
  }
  return conditions
    .map((c) => {
      const measure = c.indicatorType ?? c.counterCode ?? '?';
      const operator = c.operator ?? '?';
      const threshold = c.threshold ?? '?';
      const failure = c.onFailure ? FAILURE_LABELS[c.onFailure] ?? c.onFailure : '?';
      return `${measure} ${operator} ${threshold} → ${failure}`;
    })
    .join(' ; ');
}
