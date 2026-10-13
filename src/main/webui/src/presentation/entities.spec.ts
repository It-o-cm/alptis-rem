import { describe, it, expect } from 'vitest';
import { entityName, ALL_ENTITY_CODES, visibleEntities } from './entities';

describe('entityName', () => {
  it('maps a known code to its display name', () => {
    expect(entityName('ALPTIS_ASSURANCES')).toBe('Alptis Assurances');
    expect(entityName('UNEP')).toBe('UNEP');
  });

  it('falls back to the raw code when unknown', () => {
    expect(entityName('WHATEVER')).toBe('WHATEVER');
  });
});

describe('ALL_ENTITY_CODES', () => {
  it('lists every known entity code in display order', () => {
    expect(ALL_ENTITY_CODES).toEqual([
      'ALPTIS_ASSURANCES',
      'UNEP',
      'PROMANTIS_CONSEIL',
      'INSURED_SERVICES',
    ]);
  });
});

describe('visibleEntities', () => {
  it('expands the wildcard to every entity', () => {
    expect(visibleEntities(['*'])).toEqual(ALL_ENTITY_CODES);
  });

  it('keeps an explicit list as given', () => {
    expect(visibleEntities(['UNEP', 'ALPTIS_ASSURANCES'])).toEqual(['UNEP', 'ALPTIS_ASSURANCES']);
  });

  it('returns an empty list for no entities', () => {
    expect(visibleEntities([])).toEqual([]);
  });
});
