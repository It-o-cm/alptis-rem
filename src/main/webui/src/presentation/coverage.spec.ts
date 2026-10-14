import { describe, it, expect } from 'vitest';
import {
  remunerationModeLabel,
  contractStatusLabel,
  REMUNERATION_MODE_OPTIONS,
  CONTRACT_STATUS_OPTIONS,
} from './coverage';

describe('coverage labels', () => {
  it('labels remuneration modes, a dash for none, and echoes an unknown one', () => {
    expect(remunerationModeLabel('LINEAR')).toBe('Linéaire');
    expect(remunerationModeLabel('UPFRONT')).toBe('Précompté');
    expect(remunerationModeLabel(null)).toBe('—');
    expect(remunerationModeLabel('XXX')).toBe('XXX');
  });

  it('labels membership statuses, a dash for none, and echoes an unknown one', () => {
    expect(contractStatusLabel('PENDING')).toBe('Enregistrée');
    expect(contractStatusLabel('IN_FORCE')).toBe('En vigueur');
    expect(contractStatusLabel('SUSPENDED')).toBe('Mise en réduction');
    expect(contractStatusLabel('TERMINATED')).toBe('Résiliée');
    expect(contractStatusLabel('WITHDRAWN')).toBe('Renonciation');
    expect(contractStatusLabel('NOT_PURSUED')).toBe('Sans suite');
    expect(contractStatusLabel('LITIGATION')).toBe('Contentieux');
    expect(contractStatusLabel(null)).toBe('—');
    expect(contractStatusLabel('XXX')).toBe('XXX');
  });

  it('offers every remuneration mode as an option with its label', () => {
    expect(REMUNERATION_MODE_OPTIONS).toEqual([
      { value: 'LINEAR', label: 'Linéaire' },
      { value: 'UPFRONT', label: 'Précompté' },
    ]);
  });

  it('offers every membership status as an option with its label', () => {
    expect(CONTRACT_STATUS_OPTIONS).toContainEqual({ value: 'IN_FORCE', label: 'En vigueur' });
    expect(CONTRACT_STATUS_OPTIONS).toContainEqual({ value: 'NOT_PURSUED', label: 'Sans suite' });
    expect(CONTRACT_STATUS_OPTIONS.every((o) => o.value.length > 0 && o.label.length > 0)).toBe(true);
    expect(CONTRACT_STATUS_OPTIONS).toHaveLength(7);
  });
});
