import { describe, it, expect } from 'vitest';
import {
  domainOf,
  project,
  niceStep,
  ticks,
  bar,
  stack,
  groupOthers,
  donut,
  polar,
  arcPath,
  linePoints,
  gaugeFraction,
} from './charts';

describe('domainOf', () => {
  it('straddles zero for positive, negative and mixed data', () => {
    expect(domainOf([10, 40])).toEqual({ min: 0, max: 40 });
    expect(domainOf([-30, -5])).toEqual({ min: -30, max: 0 });
    expect(domainOf([-20, 100])).toEqual({ min: -20, max: 100 });
  });

  it('gives a unit domain for empty or all-zero data', () => {
    expect(domainOf([])).toEqual({ min: 0, max: 1 });
    expect(domainOf([0, 0])).toEqual({ min: 0, max: 1 });
  });
});

describe('project', () => {
  it('maps a value onto a pixel span', () => {
    expect(project(50, { min: 0, max: 100 }, 0, 200)).toBe(100);
  });

  it('maps a degenerate domain to the span start', () => {
    expect(project(5, { min: 5, max: 5 }, 10, 200)).toBe(10);
  });
});

describe('niceStep', () => {
  it('rounds up to a 1/2/5 x 10^n step', () => {
    expect(niceStep(0)).toBe(1);
    expect(niceStep(1)).toBe(1);
    expect(niceStep(1.5)).toBe(2);
    expect(niceStep(4)).toBe(5);
    expect(niceStep(8)).toBe(10);
    expect(niceStep(800)).toBe(1000);
  });
});

describe('ticks', () => {
  it('returns the single value of a degenerate domain', () => {
    expect(ticks({ min: 5, max: 5 }, 4)).toEqual([5]);
  });

  it('spans a domain crossing zero with a clean zero tick', () => {
    const t = ticks({ min: -20, max: 100 }, 4);
    expect(t[0]).toBeLessThanOrEqual(-20);
    expect(t[t.length - 1]).toBeGreaterThanOrEqual(100);
    expect(t).toContain(0);
  });
});

describe('bar', () => {
  it('grows a positive bar up from the zero line', () => {
    const b = bar(50, { min: 0, max: 100 }, 0, 200);
    expect(b).toEqual({ start: 0, length: 100, negative: false });
  });

  it('marks and sizes a negative bar', () => {
    const b = bar(-20, { min: -20, max: 100 }, 0, 120);
    expect(b.negative).toBe(true);
    expect(b.length).toBeCloseTo(20, 5);
  });
});

describe('stack', () => {
  it('accumulates segments in order', () => {
    expect(stack([10, 30])).toEqual([
      { start: 0, end: 10 },
      { start: 10, end: 40 },
    ]);
    expect(stack([])).toEqual([]);
  });
});

describe('groupOthers', () => {
  it('keeps everything when within the limit', () => {
    const data = [{ label: 'A', value: 1 }];
    expect(groupOthers(data, 3)).toEqual(data);
  });

  it('merges the smallest into Autres beyond the limit', () => {
    const grouped = groupOthers(
      [
        { label: 'A', value: 100 },
        { label: 'B', value: 50 },
        { label: 'C', value: 5 },
        { label: 'D', value: 3 },
      ],
      2,
    );
    expect(grouped.map((d) => d.label)).toEqual(['A', 'B', 'Autres']);
    expect(grouped[2].value).toBe(8);
  });
});

describe('donut', () => {
  it('splits the circle by magnitude, clockwise from the top', () => {
    const slices = donut(
      [
        { label: 'A', value: 75 },
        { label: 'B', value: 25 },
      ],
      6,
    );
    expect(slices[0].startAngle).toBe(0);
    expect(slices[0].fraction).toBeCloseTo(0.75, 5);
    expect(slices[1].endAngle).toBeCloseTo(2 * Math.PI, 5);
  });

  it('gives zero-width slices for all-zero data', () => {
    const slices = donut([{ label: 'A', value: 0 }], 6);
    expect(slices[0].fraction).toBe(0);
    expect(slices[0].startAngle).toBe(slices[0].endAngle);
  });
});

describe('polar', () => {
  it('places the top of the circle straight up', () => {
    const p = polar(50, 50, 10, 0);
    expect(p.x).toBeCloseTo(50, 5);
    expect(p.y).toBeCloseTo(40, 5);
  });
});

describe('arcPath', () => {
  it('draws a small arc with the large-arc flag down', () => {
    expect(arcPath(50, 50, 40, 20, 0, Math.PI / 2)).toContain('0 0 1');
  });

  it('draws a wide arc with the large-arc flag up', () => {
    expect(arcPath(50, 50, 40, 20, 0, 1.5 * Math.PI)).toContain('0 1 1');
  });

  it('splits a full turn into two half arcs', () => {
    const d = arcPath(50, 50, 40, 20, 0, 2 * Math.PI);
    expect(d.match(/M /g)?.length).toBe(2);
  });
});

describe('linePoints', () => {
  it('centres a single point', () => {
    const points = linePoints([5], { min: 0, max: 10 }, 0, 100, 0, 50);
    expect(points[0].x).toBe(50);
  });

  it('spreads several points evenly', () => {
    const points = linePoints([0, 10, 20], { min: 0, max: 20 }, 0, 100, 0, 50);
    expect(points.map((p) => p.x)).toEqual([0, 50, 100]);
  });
});

describe('gaugeFraction', () => {
  it('reads a non-positive target as zero', () => {
    expect(gaugeFraction(100, 0)).toBe(0);
  });

  it('clamps to the unit interval', () => {
    expect(gaugeFraction(150, 100)).toBe(1);
    expect(gaugeFraction(-10, 100)).toBe(0);
    expect(gaugeFraction(40, 100)).toBeCloseTo(0.4, 5);
  });
});
