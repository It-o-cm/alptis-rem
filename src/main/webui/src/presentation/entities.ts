// Display names for the group's legal entities, keyed by their code. The server exposes
// entity codes; the interface shows the human name in the header and screens.

const ENTITY_NAMES: Record<string, string> = {
  ALPTIS_ASSURANCES: 'Alptis Assurances',
  UNEP: 'UNEP',
  PROMANTIS_CONSEIL: 'Promantis Conseil',
  INSURED_SERVICES: 'Insured Services',
};

/** Every legal entity code of the group, in display order. */
export const ALL_ENTITY_CODES: ReadonlyArray<string> = Object.keys(ENTITY_NAMES);

/**
 * Returns the display name of an entity code, or the raw code if unknown.
 */
export function entityName(code: string): string {
  return ENTITY_NAMES[code] ?? code;
}

/**
 * Expands a session's visible entity codes into concrete codes: the wildcard `*` (seen by the
 * administrator and auditor) becomes every entity, any other list is kept as given. Used to feed
 * the entity selector so the user never types a code (ADM-03).
 */
export function visibleEntities(codes: readonly string[]): ReadonlyArray<string> {
  return codes.includes('*') ? ALL_ENTITY_CODES : codes;
}
