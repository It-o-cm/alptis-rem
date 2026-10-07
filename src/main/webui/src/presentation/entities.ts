// Display names for the group's legal entities, keyed by their code. The server exposes
// entity codes; the interface shows the human name in the header and screens.

const ENTITY_NAMES: Record<string, string> = {
  ALPTIS_ASSURANCES: 'Alptis Assurances',
  UNEP: 'UNEP',
  PROMANTIS_CONSEIL: 'Promantis Conseil',
  INSURED_SERVICES: 'Insured Services',
};

/**
 * Returns the display name of an entity code, or the raw code if unknown.
 */
export function entityName(code: string): string {
  return ENTITY_NAMES[code] ?? code;
}
