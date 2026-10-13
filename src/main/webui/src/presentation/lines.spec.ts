import { describe, it, expect } from 'vitest';
import { lineNatureLabel, lineStatusLabel, directionLabel, adjustmentStatusLabel, NATURE_OPTIONS } from './lines';

describe('commission line labels', () => {
  it('labels natures, a dash for none, and echoes an unknown one', () => {
    expect(lineNatureLabel('LINEAR')).toBe('Linéaire');
    expect(lineNatureLabel('ADVANCE')).toBe('Escompte');
    expect(lineNatureLabel('CLAWBACK')).toBe('Reprise');
    expect(lineNatureLabel('INSURER')).toBe('Commission reçue');
    expect(lineNatureLabel(null)).toBe('—');
    expect(lineNatureLabel('XXX')).toBe('XXX');
  });

  it('labels statuses, a dash for none, and echoes an unknown one', () => {
    expect(lineStatusLabel('EARNED')).toBe('Acquise');
    expect(lineStatusLabel('PAID')).toBe('Payée');
    expect(lineStatusLabel('OFFSET')).toBe('Imputée sur escompte');
    expect(lineStatusLabel(null)).toBe('—');
    expect(lineStatusLabel('XXX')).toBe('XXX');
  });

  it('labels directions, a dash for none, and echoes an unknown one', () => {
    expect(directionLabel('INBOUND')).toBe('Entrant');
    expect(directionLabel('OUTBOUND')).toBe('Sortant');
    expect(directionLabel(null)).toBe('—');
    expect(directionLabel('XXX')).toBe('XXX');
  });

  it('labels adjustment statuses, a dash for none, and echoes an unknown one', () => {
    expect(adjustmentStatusLabel('DRAFT')).toBe('Brouillon');
    expect(adjustmentStatusLabel('SUBMITTED')).toBe('Soumis');
    expect(adjustmentStatusLabel('APPROVED')).toBe('Approuvé');
    expect(adjustmentStatusLabel('REJECTED')).toBe('Rejeté');
    expect(adjustmentStatusLabel('APPLIED')).toBe('Appliqué');
    expect(adjustmentStatusLabel(null)).toBe('—');
    expect(adjustmentStatusLabel('XXX')).toBe('XXX');
  });

  it('offers every nature as a filter option with its label', () => {
    expect(NATURE_OPTIONS).toContainEqual({ value: 'LINEAR', label: 'Linéaire' });
    expect(NATURE_OPTIONS).toContainEqual({ value: 'INSURER', label: 'Commission reçue' });
    expect(NATURE_OPTIONS.every((o) => o.value.length > 0 && o.label.length > 0)).toBe(true);
    expect(NATURE_OPTIONS).toHaveLength(17);
  });
});
