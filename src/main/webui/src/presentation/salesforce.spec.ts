import { describe, it, expect } from 'vitest';
import { jobCodeLabel, JOB_CODES, measureTypeLabel } from './salesforce';

describe('sales-force presentation', () => {
  it('labels known job codes and falls back for the rest', () => {
    expect(jobCodeLabel('CAC')).toBe("Conseiller d'affaires courtage");
    expect(jobCodeLabel('DNV')).toBe('Directeur national des ventes');
    expect(jobCodeLabel('UNKNOWN')).toBe('UNKNOWN');
    expect(jobCodeLabel(null)).toBe('—');
  });

  it('lists the five job codes of the grid', () => {
    expect(JOB_CODES).toEqual(['CAC', 'RB', 'DR', 'RR', 'DNV']);
  });

  it('labels known measure types and falls back for the rest', () => {
    expect(measureTypeLabel('ABSENCE')).toBe('Absences (hors congés et RTT)');
    expect(measureTypeLabel('PRODUCTION')).toBe('Production par marché');
    expect(measureTypeLabel('SPECIFICATIONS_RECEIVED')).toBe('Cahiers des charges reçus');
    expect(measureTypeLabel('UNKNOWN')).toBe('UNKNOWN');
    expect(measureTypeLabel(null)).toBe('—');
  });
});
