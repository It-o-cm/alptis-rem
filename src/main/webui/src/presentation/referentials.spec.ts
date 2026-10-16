import { describe, it, expect } from 'vitest';
import { indicatorSourceLabel } from './referentials';

describe('referentials', () => {
  it('labels a computed indicator source and a received one', () => {
    expect(indicatorSourceLabel('COMPUTED')).toBe('Calculé en interne');
    expect(indicatorSourceLabel('demo')).toBe('Reçu');
    expect(indicatorSourceLabel(null)).toBe('Reçu');
  });
});
