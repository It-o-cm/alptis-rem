import { describe, it, expect } from 'vitest';
import { familyLabel, versionStatusClass, schemaFields, conflictLabel } from './rules';

describe('rules presentation', () => {
  it('labels known families and falls back for the rest', () => {
    expect(familyLabel('UPFRONT')).toBe('Précomptée');
    expect(familyLabel('UNKNOWN')).toBe('UNKNOWN');
    expect(familyLabel(null)).toBe('—');
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
});
