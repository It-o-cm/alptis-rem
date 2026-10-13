import { describe, it, expect } from 'vitest';
import { monthLabel } from './months';

describe('monthLabel', () => {
  it('formats an ISO month as a French month and year', () => {
    expect(monthLabel('2026-09')).toBe('septembre 2026');
    expect(monthLabel('2026-01')).toBe('janvier 2026');
    expect(monthLabel('2026-12')).toBe('décembre 2026');
    expect(monthLabel('2025-08')).toBe('août 2025');
  });

  it('returns a dash for a missing value', () => {
    expect(monthLabel(null)).toBe('—');
    expect(monthLabel(undefined)).toBe('—');
    expect(monthLabel('')).toBe('—');
  });

  it('returns a dash for an unparseable value', () => {
    expect(monthLabel('2026')).toBe('—');
    expect(monthLabel('2026-9')).toBe('—');
    expect(monthLabel('not-a-month')).toBe('—');
  });

  it('returns a dash for an out-of-range month number', () => {
    expect(monthLabel('2026-00')).toBe('—');
    expect(monthLabel('2026-13')).toBe('—');
  });
});
