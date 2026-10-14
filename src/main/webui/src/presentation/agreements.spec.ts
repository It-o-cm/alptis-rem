import { describe, it, expect } from 'vitest';
import { agreementTypeLabel, signatureStatusLabel, enrollmentStatusLabel } from './agreements';

describe('agreement labels', () => {
  it('labels agreement types, a dash for none, and echoes an unknown one', () => {
    expect(agreementTypeLabel('PARTNERSHIP')).toBe('Convention de partenariat');
    expect(agreementTypeLabel('RCD_AMENDMENT')).toBe('Avenant RCD');
    expect(agreementTypeLabel('RCP_AMENDMENT')).toBe('Avenant RCP');
    expect(agreementTypeLabel('ADVANCE_AGREEMENT')).toBe('Convention d’escompte');
    expect(agreementTypeLabel('INSURER_PROTOCOL')).toBe('Protocole assureur');
    expect(agreementTypeLabel('MANDATE')).toBe('Mandat');
    expect(agreementTypeLabel(null)).toBe('—');
    expect(agreementTypeLabel('XXX')).toBe('XXX');
  });

  it('labels signature states, a dash for none, and echoes an unknown one', () => {
    expect(signatureStatusLabel('SIGNED')).toBe('Signé');
    expect(signatureStatusLabel('NOT_SIGNED')).toBe('Non signé');
    expect(signatureStatusLabel(null)).toBe('—');
    expect(signatureStatusLabel('XXX')).toBe('XXX');
  });

  it('labels enrollment statuses, a dash for none, and echoes an unknown one', () => {
    expect(enrollmentStatusLabel('PENDING_SIGNATURE')).toBe('En attente de signature');
    expect(enrollmentStatusLabel('ACTIVE')).toBe('Active');
    expect(enrollmentStatusLabel('ENDED')).toBe('Terminée');
    expect(enrollmentStatusLabel(null)).toBe('—');
    expect(enrollmentStatusLabel('XXX')).toBe('XXX');
  });
});
