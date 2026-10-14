import { describe, it, expect } from 'vitest';
import {
  jobCodeLabel, JOB_CODES, measureTypeLabel, objectiveTypeLabel, OBJECTIVE_TYPES, componentLabel, PLAN_COMPONENTS, variableStatusLabel,
} from './salesforce';

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

  it('labels known objective types and falls back for the rest', () => {
    expect(objectiveTypeLabel('INDIVIDUAL_QUANTITATIVE')).toBe('Individuel quantitatif');
    expect(objectiveTypeLabel('COLLECTIVE_QUANTITATIVE')).toBe('Collectif quantitatif');
    expect(objectiveTypeLabel('QUALITATIVE')).toBe('Qualitatif');
    expect(objectiveTypeLabel('UNKNOWN')).toBe('UNKNOWN');
    expect(objectiveTypeLabel(null)).toBe('—');
    expect(OBJECTIVE_TYPES).toContain('COLLECTIVE_QUANTITATIVE');
  });

  it('labels known variable components and falls back for the rest', () => {
    expect(componentLabel('MONTHLY_INDIVIDUAL')).toBe('Mensuel individuel');
    expect(componentLabel('ANNUAL_COLLECTIVE')).toBe('Collectif');
    expect(componentLabel('ANNUAL_MANAGER')).toBe('Animateurs et responsables');
    expect(componentLabel('ANNUAL_DIRECT_SALES')).toBe('Vente directe');
    expect(componentLabel('MANUAL_ADJUSTMENT')).toBe('Ajustement');
    expect(componentLabel('UNKNOWN')).toBe('UNKNOWN');
    expect(componentLabel(null)).toBe('—');
    expect(PLAN_COMPONENTS).toContain('MONTHLY_INDIVIDUAL');
  });

  it('labels known variable-result statuses and falls back for the rest', () => {
    expect(variableStatusLabel('CALCULATED')).toBe('Calculé');
    expect(variableStatusLabel('VALIDATED')).toBe('Validé');
    expect(variableStatusLabel('EXPORTED')).toBe('Exporté');
    expect(variableStatusLabel('UNKNOWN')).toBe('UNKNOWN');
    expect(variableStatusLabel(null)).toBe('—');
  });
});
