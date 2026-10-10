import { describe, it, expect } from 'vitest';
import { workSectionsForRoles, actsOn, orderItemsForRoles } from './menu';

describe('menu tailoring by role', () => {
  describe('workSectionsForRoles', () => {
    it('gives a single role its work rubrics', () => {
      expect(workSectionsForRoles(['BUSINESS_VALIDATOR'])).toEqual(['Cycle mensuel']);
      expect(workSectionsForRoles(['SALES_REP'])).toEqual(['Forces de vente']);
    });

    it('unions the work rubrics of a multi-role user without duplicates', () => {
      expect(workSectionsForRoles(['FINANCE_CONTROLLER', 'OPERATOR'])).toEqual([
        'Cycle mensuel',
        'Comptabilité',
        'Distributeurs',
        'Flux',
      ]);
    });

    it('ignores unknown roles', () => {
      expect(workSectionsForRoles(['NOT_A_ROLE'])).toEqual([]);
      expect(workSectionsForRoles([])).toEqual([]);
    });
  });

  describe('actsOn', () => {
    it('is true when one of the user roles acts on the screen', () => {
      expect(actsOn('rules', ['BUSINESS_ADMIN'])).toBe(true);
      expect(actsOn('payment', ['FINANCE_CONTROLLER'])).toBe(true);
    });

    it('is false when no user role acts on the screen', () => {
      expect(actsOn('rules', ['VIEWER'])).toBe(false);
    });

    it('is false for a consult-only screen absent from the actor map', () => {
      expect(actsOn('dashboards', ['BUSINESS_ADMIN'])).toBe(false);
      expect(actsOn('contractSearch', ['OPERATOR'])).toBe(false);
    });
  });

  describe('orderItemsForRoles', () => {
    it('puts acting screens before consult-only ones, keeping order within each group', () => {
      const items = [
        { name: 'dashboards' },
        { name: 'rules' },
        { name: 'contractSearch' },
        { name: 'enrollments' },
      ];
      expect(orderItemsForRoles(items, ['BUSINESS_ADMIN']).map((i) => i.name)).toEqual([
        'rules',
        'enrollments',
        'dashboards',
        'contractSearch',
      ]);
    });

    it('keeps the original order when the user acts on nothing', () => {
      const items = [{ name: 'rules' }, { name: 'dashboards' }];
      expect(orderItemsForRoles(items, ['VIEWER']).map((i) => i.name)).toEqual(['rules', 'dashboards']);
    });
  });
});
