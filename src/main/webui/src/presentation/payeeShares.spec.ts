import { describe, it, expect } from 'vitest';
import { formatShare, formatShareList } from './payeeShares';

describe('payeeShares', () => {
  it('formats an integer percentage without decimals', () => {
    expect(formatShare('0.9')).toBe('90 %');
  });

  it('formats a fractional percentage with two decimals', () => {
    expect(formatShare('0.125')).toBe('12.50 %');
  });

  it('shows a dash for a null fraction', () => {
    expect(formatShare(null)).toBe('—');
  });

  it('joins several shares with a middot', () => {
    expect(
      formatShareList([
        { payeeType: 'DISTRIBUTOR', payeeCode: 'P1', share: '0.9' },
        { payeeType: 'GROUP', payeeCode: 'G1', share: '0.1' },
      ]),
    ).toBe('DISTRIBUTOR P1 90 % · GROUP G1 10 %');
  });

  it('falls back to question marks for missing type or code', () => {
    expect(formatShareList([{ payeeType: null, payeeCode: null, share: null }])).toBe('? ? —');
  });

  it('shows a dash for an empty share list', () => {
    expect(formatShareList([])).toBe('—');
  });
});
