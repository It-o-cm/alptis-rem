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
