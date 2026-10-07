import { describe, it, expect } from 'vitest';
import { entityName } from './entities';

describe('entityName', () => {
  it('maps a known code to its display name', () => {
    expect(entityName('ALPTIS_ASSURANCES')).toBe('Alptis Assurances');
    expect(entityName('UNEP')).toBe('UNEP');
  });

  it('falls back to the raw code when unknown', () => {
    expect(entityName('WHATEVER')).toBe('WHATEVER');
  });
});
