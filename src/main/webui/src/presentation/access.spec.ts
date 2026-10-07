import { describe, it, expect } from 'vitest';
import { canAccess, canEditEntityParameters, canManageRoleMappings } from './access';

describe('access', () => {
  it('grants a screen to an allowed role and denies others', () => {
    expect(canAccess('adminEntities', ['BUSINESS_ADMIN'])).toBe(true);
    expect(canAccess('adminEntities', ['SALES_REP'])).toBe(false);
    expect(canAccess('adminRoleMappings', ['IT_ADMIN'])).toBe(true);
    expect(canAccess('adminRoleMappings', ['OPERATOR'])).toBe(false);
  });

  it('opens screens with no explicit rule to everyone', () => {
    expect(canAccess('imports', ['SALES_REP'])).toBe(true);
  });

  it('restricts entity parameter editing to the business administrator', () => {
    expect(canEditEntityParameters(['BUSINESS_ADMIN'])).toBe(true);
    expect(canEditEntityParameters(['OPERATOR'])).toBe(false);
  });

  it('restricts role-mapping management to the technical administrator', () => {
    expect(canManageRoleMappings(['IT_ADMIN'])).toBe(true);
    expect(canManageRoleMappings(['AUDITOR'])).toBe(false);
  });
});
