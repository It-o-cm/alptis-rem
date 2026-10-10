import { describe, it, expect } from 'vitest';
import { NAV_SECTIONS, allNavItems, sectionHeadingOf } from './navigation';

describe('navigation', () => {
  it('reproduces the sections of the mock-up in order', () => {
    expect(NAV_SECTIONS.map((s) => s.heading)).toEqual([
      'Cycle mensuel',
      'Distributeurs',
      'Règles',
      'Flux',
      'Référentiels',
      'Comptabilité',
      'Restitutions',
      'Administration',
      'Forces de vente',
    ]);
  });

  it('exposes every entry, starting with the periods home page', () => {
    const items = allNavItems();
    expect(items).toHaveLength(42);
    expect(items[0]).toEqual({ name: 'periods', path: '/', label: 'Périodes' });
  });

  it('has unique route names and paths', () => {
    const items = allNavItems();
    expect(new Set(items.map((i) => i.name)).size).toBe(items.length);
    expect(new Set(items.map((i) => i.path)).size).toBe(items.length);
  });

  describe('sectionHeadingOf', () => {
    it('returns the rubric heading of a known route', () => {
      expect(sectionHeadingOf('periods')).toBe('Cycle mensuel');
      expect(sectionHeadingOf('fdvCommercial')).toBe('Forces de vente');
    });

    it('returns an empty string for an unknown or missing route', () => {
      expect(sectionHeadingOf('nope')).toBe('');
      expect(sectionHeadingOf(undefined)).toBe('');
    });
  });
});
