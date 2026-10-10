// The chart palette (brief RESTITUTIONS §4.2): one colour means the same thing on every screen, the
// ramp is drawn from the mock-up colours (src/styles.css), stays distinguishable for a colour-blind
// reader and is never the only signal — every chart also carries a text legend and the values. Pure
// look-ups, unit-tested to 100 %.

/** The categorical ramp: the mock-up primary plus the four status hues, in a fixed order. */
export const CHART_COLORS = ['#0e5a8a', '#8a4b06', '#14603a', '#8f1d16', '#0b4a73', '#3d4a59'];

/** The colour of the positive/received series (actual, received, paid-in). */
export const SERIES_POSITIVE = '#0e5a8a';

/** The colour of the contrasting series (simulated, paid-out). */
export const SERIES_CONTRAST = '#8a4b06';

/** The colour of a negative value (clawbacks, debit balances). */
export const SERIES_NEGATIVE = '#8f1d16';

/** Fixed colours for the semantic series that must look the same everywhere. */
const SEMANTIC: Record<string, string> = {
  reel: SERIES_POSITIVE,
  simule: SERIES_CONTRAST,
  recu: SERIES_POSITIVE,
  paye: SERIES_CONTRAST,
  marge: '#14603a',
};

/**
 * The colour of the n-th category, cycling through the ramp.
 *
 * @param index the category index
 * @returns the colour
 */
export function paletteColor(index: number): string {
  return CHART_COLORS[((index % CHART_COLORS.length) + CHART_COLORS.length) % CHART_COLORS.length];
}

/**
 * The stable colour of a key within an ordered set of keys, so the same key keeps its colour across a
 * screen; an unknown key falls back to the last ramp colour.
 *
 * @param key  the key
 * @param keys the ordered keys
 * @returns the colour
 */
export function colorForKey(key: string, keys: string[]): string {
  const index = keys.indexOf(key);
  return index < 0 ? CHART_COLORS[CHART_COLORS.length - 1] : paletteColor(index);
}

/**
 * The fixed colour of a semantic series (reel, simule, recu, paye, marge), or the primary when the
 * series is not semantic.
 *
 * @param series the series id
 * @returns the colour
 */
export function semanticColor(series: string): string {
  return SEMANTIC[series] ?? SERIES_POSITIVE;
}
