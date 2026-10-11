import { describe, it, expect } from 'vitest';
import { lineNatureLabel, lineStatusLabel, directionLabel } from './lines';

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
});
