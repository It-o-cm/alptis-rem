import { describe, it, expect } from 'vitest';
import { distributorTypeLabel, partnerStatusLabel } from './distributors';

describe('distributor labels', () => {
  it('labels distributor types, a dash for none, and echoes an unknown one', () => {
    expect(distributorTypeLabel('INDEPENDENT')).toBe('Indépendant');
    expect(distributorTypeLabel('GROUP_MEMBER')).toBe('Membre de groupement');
    expect(distributorTypeLabel('REMOTE_SALES')).toBe('Vente à distance');
    expect(distributorTypeLabel('AGENT')).toBe('Mandataire');
    expect(distributorTypeLabel(null)).toBe('—');
    expect(distributorTypeLabel('XXX')).toBe('XXX');
  });

  it('labels partner statuses, a dash for none, and echoes an unknown one', () => {
    expect(partnerStatusLabel('CANDIDATE')).toBe('En cours de codification');
    expect(partnerStatusLabel('ACTIVE')).toBe('Actif');
    expect(partnerStatusLabel('SUSPENDED')).toBe('Suspendu');
    expect(partnerStatusLabel('TERMINATED')).toBe('Résilié');
    expect(partnerStatusLabel(null)).toBe('—');
    expect(partnerStatusLabel('XXX')).toBe('XXX');
  });
});
