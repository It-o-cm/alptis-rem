import { describe, it, expect } from 'vitest';
import { ROLES, roleLabel } from './roles';

describe('roles', () => {
  it('lists the nine application roles', () => {
    expect(ROLES).toHaveLength(9);
    expect(ROLES).toContain('BUSINESS_ADMIN');
    expect(ROLES).toContain('SALES_REP');
  });

  it('maps a known role code to its French label', () => {
    expect(roleLabel('BUSINESS_VALIDATOR')).toBe('valideur métier');
    expect(roleLabel('FINANCE_CONTROLLER')).toBe('contrôleur Finance');
  });

  it('falls back to the raw code for an unknown role', () => {
    expect(roleLabel('WHATEVER')).toBe('WHATEVER');
  });
});
