import { describe, it, expect } from 'vitest';
import { formatAmount, formatPercent, formatDate } from './format';

// Intl inserts narrow/no-break spaces in fr-FR; normalise them for stable assertions.
const norm = (s: string) => s.replace(/[  ]/g, ' ');

describe('formatAmount', () => {
  it('formats an amount in euros, French style', () => {
    expect(norm(formatAmount(1912480.1))).toBe('1 912 480,10 €');
    expect(norm(formatAmount(110))).toBe('110,00 €');
    expect(norm(formatAmount(-148300.25))).toBe('-148 300,25 €');
  });

  it('returns a dash for a missing value', () => {
    expect(formatAmount(null)).toBe('—');
    expect(formatAmount(undefined)).toBe('—');
  });
});

describe('formatPercent', () => {
  it('formats a decimal fraction as a percentage', () => {
    expect(norm(formatPercent(0.15))).toBe('15 %');
    expect(norm(formatPercent(0.035))).toBe('3,5 %');
  });

  it('returns a dash for a missing value', () => {
    expect(formatPercent(null)).toBe('—');
    expect(formatPercent(undefined)).toBe('—');
  });
});

describe('formatDate', () => {
  it('formats an ISO date as a French date', () => {
    expect(formatDate('2026-09-15')).toBe('15/09/2026');
  });

  it('returns a dash for a missing or unparseable value', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate(undefined)).toBe('—');
    expect(formatDate('')).toBe('—');
    expect(formatDate('not-a-date')).toBe('—');
  });
});
