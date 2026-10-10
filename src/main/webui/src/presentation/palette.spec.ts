import { describe, it, expect } from 'vitest';
import { CHART_COLORS, paletteColor, colorForKey, semanticColor, SERIES_POSITIVE } from './palette';

describe('paletteColor', () => {
  it('returns the ramp colour and wraps around', () => {
    expect(paletteColor(0)).toBe(CHART_COLORS[0]);
    expect(paletteColor(CHART_COLORS.length)).toBe(CHART_COLORS[0]);
    expect(paletteColor(-1)).toBe(CHART_COLORS[CHART_COLORS.length - 1]);
  });
});

describe('colorForKey', () => {
  it('gives a stable colour by position', () => {
    expect(colorForKey('B', ['A', 'B', 'C'])).toBe(paletteColor(1));
  });

  it('falls back to the last colour for an unknown key', () => {
    expect(colorForKey('Z', ['A'])).toBe(CHART_COLORS[CHART_COLORS.length - 1]);
  });
});

describe('semanticColor', () => {
  it('fixes the colour of a semantic series', () => {
    expect(semanticColor('reel')).toBe(SERIES_POSITIVE);
  });

  it('falls back to the positive colour for a non-semantic series', () => {
    expect(semanticColor('autre')).toBe(SERIES_POSITIVE);
  });
});
