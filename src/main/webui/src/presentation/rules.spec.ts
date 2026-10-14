import { describe, it, expect } from 'vitest';
import { familyLabel, versionStatusLabel, versionStatusClass, schemaFields, conflictLabel, repriseLabel, conditionsLabel } from './rules';

describe('rules presentation', () => {
  it('labels known families and falls back for the rest', () => {
    expect(familyLabel('UPFRONT')).toBe('Précomptée');
    expect(familyLabel('UNKNOWN')).toBe('UNKNOWN');
    expect(familyLabel(null)).toBe('—');
  });

  it('labels version statuses in French, a dash for none, and echoes an unknown one', () => {
    expect(versionStatusLabel('DRAFT')).toBe('Brouillon');
    expect(versionStatusLabel('APPROVED')).toBe('Approuvée');
    expect(versionStatusLabel('ACTIVE')).toBe('En vigueur');
    expect(versionStatusLabel('RETIRED')).toBe('Retirée');
    expect(versionStatusLabel(null)).toBe('—');
    expect(versionStatusLabel('XXX')).toBe('XXX');
  });

  it('maps version statuses to tag classes', () => {
    expect(versionStatusClass('ACTIVE')).toBe('tag ok');
    expect(versionStatusClass('APPROVED')).toBe('tag info');
    expect(versionStatusClass('RETIRED')).toBe('tag mute');
    expect(versionStatusClass('DRAFT')).toBe('tag');
    expect(versionStatusClass(null)).toBe('tag');
  });

  it('extracts schema fields with their type and requiredness', () => {
    const schema = JSON.stringify({
      type: 'object',
      properties: { allowedRates: { type: 'array' }, firstYearDays: { type: 'integer' } },
      required: ['allowedRates'],
    });
    const fields = schemaFields(schema);
    expect(fields).toEqual([
      { name: 'allowedRates', type: 'array', required: true },
      { name: 'firstYearDays', type: 'integer', required: false },
    ]);
  });

  it('defaults a property without a type to string', () => {
    expect(schemaFields(JSON.stringify({ properties: { x: {} } }))).toEqual([
      { name: 'x', type: 'string', required: false },
    ]);
  });

  it('returns no fields for malformed, non-object or property-less schemas', () => {
    expect(schemaFields('{bad')).toEqual([]);
    expect(schemaFields('42')).toEqual([]);
    expect(schemaFields(JSON.stringify({ type: 'object' }))).toEqual([]);
  });

  it('formats a conflict list', () => {
    expect(conflictLabel([])).toBe('Aucun conflit');
    expect(conflictLabel(['R1 v1', 'R2 v1'])).toBe('R1 v1, R2 v1');
  });

  it('summarises the reprise policy and payment schedule', () => {
    expect(repriseLabel(null, null)).toBe('—');
    expect(repriseLabel('{"mode":"PRORATA"}', null)).toBe('Reprise {"mode":"PRORATA"}');
    expect(repriseLabel(null, '{"months":12}')).toBe('Échéancier {"months":12}');
    expect(repriseLabel('{"mode":"PRORATA"}', '{"months":12}'))
      .toBe('Reprise {"mode":"PRORATA"} · Échéancier {"months":12}');
  });

  it('summarises payment conditions, with labels and fallbacks', () => {
    expect(conditionsLabel([])).toBe('—');
    expect(conditionsLabel([
      { indicatorType: 'UNPAID_RATE', counterCode: null, operator: 'LT', threshold: 0.05, onFailure: 'DEFER' },
      { indicatorType: null, counterCode: 'NEW_BUSINESS', operator: 'GE', threshold: 10, onFailure: 'FORFEIT' },
    ])).toBe('UNPAID_RATE LT 0.05 → Report ; NEW_BUSINESS GE 10 → Perte');
    // Every field missing falls back to its placeholder, including an unknown failure code.
    expect(conditionsLabel([
      { indicatorType: null, counterCode: null, operator: null, threshold: null, onFailure: null },
      { indicatorType: 'X', counterCode: null, operator: 'EQ', threshold: 1, onFailure: 'OTHER' },
    ])).toBe('? ? ? → ? ; X EQ 1 → OTHER');
  });
});
