import { describe, it, expect } from 'vitest';
import { transferStatusLabel } from './transfers';

describe('transfer labels', () => {
  it('labels transfer statuses, a dash for none, and echoes an unknown one', () => {
    expect(transferStatusLabel('PENDING')).toBe('En attente');
    expect(transferStatusLabel('PROCESSED')).toBe('Traité');
    expect(transferStatusLabel(null)).toBe('—');
    expect(transferStatusLabel('XXX')).toBe('XXX');
  });
});
