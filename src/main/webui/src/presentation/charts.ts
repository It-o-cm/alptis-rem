// Pure geometry for the home-made SVG charts (brief RESTITUTIONS §4.1). No DOM, no SVG side effect:
// every scale, tick, stack, slice angle and gauge arc is computed here and unit-tested to 100 %, so
// the chart components only place the shapes these functions describe. The server computes the data;
// these functions only lay it out.

/** A value domain, always straddling zero so bars grow from the zero line. */
export interface Domain {
  min: number;
  max: number;
}

/** A positioned bar: its offset and length along the value axis, and whether it is negative. */
export interface Bar {
  start: number;
  length: number;
  negative: boolean;
}

/** One donut slice with its angles (radians, clockwise from twelve o'clock) and share. */
export interface Slice {
  label: string;
  value: number;
  fraction: number;
  startAngle: number;
  endAngle: number;
}

/** A labelled value, the common input of the categorical charts. */
export interface Datum {
  label: string;
  value: number;
}

/**
 * The value domain of a series, always including zero. All-zero (or empty) data gives {0, 1} so the
 * scale never divides by zero.
 */
export function domainOf(values: number[]): Domain {
  let min = 0;
  let max = 0;
  for (const v of values) {
    if (v < min) {
      min = v;
    }
    if (v > max) {
      max = v;
    }
  }
  if (min === 0 && max === 0) {
    return { min: 0, max: 1 };
  }
  return { min, max };
}

/**
 * Projects a value onto a pixel span [pxMin, pxMax] over a domain. A degenerate domain (min === max)
 * projects to pxMin.
 */
export function project(value: number, domain: Domain, pxMin: number, pxMax: number): number {
  if (domain.max === domain.min) {
    return pxMin;
  }
  const ratio = (value - domain.min) / (domain.max - domain.min);
  return pxMin + ratio * (pxMax - pxMin);
}

/** Rounds a positive magnitude up to the next 1/2/5 × 10ⁿ step (for nice axis bounds and ticks). */
export function niceStep(magnitude: number): number {
  if (magnitude <= 0) {
    return 1;
  }
  const power = Math.pow(10, Math.floor(Math.log10(magnitude)));
  const unit = magnitude / power;
  let factor = 10;
  if (unit <= 1) {
    factor = 1;
  } else if (unit <= 2) {
    factor = 2;
  } else if (unit <= 5) {
    factor = 5;
  }
  return factor * power;
}

/**
 * Builds up to {@code count}+1 evenly-spaced, nicely-rounded ticks spanning a domain (both bounds
 * included). A degenerate domain yields its single value.
 */
export function ticks(domain: Domain, count: number): number[] {
  if (domain.max === domain.min) {
    return [domain.min];
  }
  const step = niceStep((domain.max - domain.min) / count);
  const start = Math.floor(domain.min / step) * step;
  const end = Math.ceil(domain.max / step) * step;
  const result: number[] = [];
  for (let t = start; t <= end + step / 1000; t += step) {
    result.push(Math.abs(t) < step / 1000 ? 0 : t);
  }
  return result;
}

/**
 * Positions one bar of {@code value} over a domain along a pixel span: its start at the zero line and
 * its signed length towards the value.
 */
export function bar(value: number, domain: Domain, pxMin: number, pxMax: number): Bar {
  const zero = project(0, domain, pxMin, pxMax);
  const end = project(value, domain, pxMin, pxMax);
  return { start: Math.min(zero, end), length: Math.abs(end - zero), negative: value < 0 };
}

/**
 * Stacks a row of non-negative values into cumulative segments, each with its start and end offset
 * from zero (in value units), in input order.
 */
export function stack(values: number[]): Array<{ start: number; end: number }> {
  const segments: Array<{ start: number; end: number }> = [];
  let cursor = 0;
  for (const v of values) {
    segments.push({ start: cursor, end: cursor + v });
    cursor += v;
  }
  return segments;
}

/**
 * Keeps the {@code max} largest-by-magnitude data and merges the remainder into a single "Autres"
 * entry (omitted when nothing is left over). Input order of the kept entries is preserved.
 */
export function groupOthers(data: Datum[], max: number): Datum[] {
  if (data.length <= max) {
    return data.slice();
  }
  const ranked = data
    .map((d, i) => ({ d, i }))
    .sort((a, b) => Math.abs(b.d.value) - Math.abs(a.d.value));
  const keptIndices = new Set(ranked.slice(0, max).map((e) => e.i));
  const kept: Datum[] = [];
  let others = 0;
  data.forEach((d, i) => {
    if (keptIndices.has(i)) {
      kept.push(d);
    } else {
      others += d.value;
    }
  });
  kept.push({ label: 'Autres', value: others });
  return kept;
}

/**
 * Lays out donut slices over the non-negative magnitudes of the data (after grouping beyond
 * {@code maxSlices}), clockwise from the top. All-zero data gives zero-width slices.
 */
export function donut(data: Datum[], maxSlices: number): Slice[] {
  const grouped = groupOthers(data, maxSlices);
  const total = grouped.reduce((sum, d) => sum + Math.abs(d.value), 0);
  const slices: Slice[] = [];
  let angle = 0;
  for (const d of grouped) {
    const fraction = total === 0 ? 0 : Math.abs(d.value) / total;
    const sweep = fraction * 2 * Math.PI;
    slices.push({ label: d.label, value: d.value, fraction, startAngle: angle, endAngle: angle + sweep });
    angle += sweep;
  }
  return slices;
}

/** The point at {@code angle} (clockwise from twelve o'clock) and radius {@code r} around a centre. */
export function polar(cx: number, cy: number, r: number, angle: number): { x: number; y: number } {
  return { x: cx + r * Math.sin(angle), y: cy - r * Math.cos(angle) };
}

/**
 * The SVG path of a donut (or pie, when {@code rInner} is 0) segment between two angles. A full turn
 * (or more) is drawn as two half arcs so the large-arc flag stays unambiguous.
 */
export function arcPath(cx: number, cy: number, rOuter: number, rInner: number, a0: number, a1: number): string {
  const sweep = a1 - a0;
  if (sweep >= 2 * Math.PI) {
    const mid = a0 + Math.PI;
    return arcPath(cx, cy, rOuter, rInner, a0, mid) + ' ' + arcPath(cx, cy, rOuter, rInner, mid, a1);
  }
  const large = sweep > Math.PI ? 1 : 0;
  const o0 = polar(cx, cy, rOuter, a0);
  const o1 = polar(cx, cy, rOuter, a1);
  const i1 = polar(cx, cy, rInner, a1);
  const i0 = polar(cx, cy, rInner, a0);
  return [
    `M ${round(o0.x)} ${round(o0.y)}`,
    `A ${round(rOuter)} ${round(rOuter)} 0 ${large} 1 ${round(o1.x)} ${round(o1.y)}`,
    `L ${round(i1.x)} ${round(i1.y)}`,
    `A ${round(rInner)} ${round(rInner)} 0 ${large} 0 ${round(i0.x)} ${round(i0.y)}`,
    'Z',
  ].join(' ');
}

/**
 * The polyline points of a line chart: the values spread evenly across [pxLeft, pxRight] and projected
 * onto [pxBottom, pxTop]. A single value is centred.
 */
export function linePoints(values: number[], domain: Domain, pxLeft: number, pxRight: number, pxTop: number,
  pxBottom: number): Array<{ x: number; y: number }> {
  return values.map((v, i) => {
    const x = values.length === 1
      ? (pxLeft + pxRight) / 2
      : pxLeft + (i / (values.length - 1)) * (pxRight - pxLeft);
    return { x, y: project(v, domain, pxBottom, pxTop) };
  });
}

/**
 * The achievement fraction of a gauge, clamped to [0, 1]; a non-positive target reads as zero.
 */
export function gaugeFraction(value: number, target: number): number {
  if (target <= 0) {
    return 0;
  }
  return Math.max(0, Math.min(1, value / target));
}

/**
 * Rounds a coordinate to two decimals for compact, stable SVG paths.
 */
function round(value: number): number {
  return Math.round(value * 100) / 100;
}
