import { describe, it, expect } from 'vitest';
import { advanceAgreementStatusLabel, advanceStatusLabel } from './advance';

describe('advance labels', () => {
  it('labels discount-agreement statuses, a dash for none, and echoes an unknown one', () => {
    expect(advanceAgreementStatusLabel('VALID')).toBe('Valide');
    expect(advanceAgreementStatusLabel('SUSPENDED')).toBe('Suspendue');
    expect(advanceAgreementStatusLabel('CLOSED')).toBe('Clôturée');
    expect(advanceAgreementStatusLabel(null)).toBe('—');
    expect(advanceAgreementStatusLabel('XXX')).toBe('XXX');
  });

  it('labels advance-position statuses, a dash for none, and echoes an unknown one', () => {
    expect(advanceStatusLabel('PENDING')).toBe('En attente');
    expect(advanceStatusLabel('ACTIVE')).toBe('Active');
    expect(advanceStatusLabel('SETTLED')).toBe('Apurée');
    expect(advanceStatusLabel('CANCELLED')).toBe('Annulée');
    expect(advanceStatusLabel(null)).toBe('—');
    expect(advanceStatusLabel('XXX')).toBe('XXX');
  });
});
