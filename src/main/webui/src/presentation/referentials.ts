/**
 * Presentation helpers for the referential consultation screens (REF-01, REF-04, REF-07).
 */

/**
 * French label of a quality indicator's source: values the application derives itself (REF-07,
 * INT-23) versus those received from the CRM.
 *
 * @param source the stored source marker, or null
 * @returns 'Calculé en interne' for a computed value, 'Reçu' otherwise
 */
export function indicatorSourceLabel(source: string | null): string {
  return source === 'COMPUTED' ? 'Calculé en interne' : 'Reçu';
}
