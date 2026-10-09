import { describe, it, expect } from 'vitest';
import {
  cutoffViewLabel,
  flowDirectionLabel,
  exportStatusLabel,
  canProduceExport,
} from './reporting';

describe('cut-off view labels', () => {
  it('labels each maille and echoes an unknown one', () => {
    expect(cutoffViewLabel('COVERAGE')).toBe('Garantie');
    expect(cutoffViewLabel('DISTRIBUTOR')).toBe('Distributeur');
    expect(cutoffViewLabel('PRODUCT')).toBe('Produit');
    expect(cutoffViewLabel('ENTITY')).toBe('Entité');
    expect(cutoffViewLabel('WAT')).toBe('WAT');
  });
});

describe('flow direction labels', () => {
  it('labels each direction and echoes an unknown one', () => {
    expect(flowDirectionLabel('INBOUND')).toBe('Entrant');
    expect(flowDirectionLabel('OUTBOUND')).toBe('Sortant');
    expect(flowDirectionLabel('WAT')).toBe('WAT');
  });
});

describe('export status labels', () => {
  it('labels each status and echoes an unknown one', () => {
    expect(exportStatusLabel('GENERATED')).toBe('Généré');
    expect(exportStatusLabel('DELIVERED')).toBe('Déposé');
    expect(exportStatusLabel('FAILED')).toBe('Échec');
    expect(exportStatusLabel('WAT')).toBe('WAT');
  });
});

describe('export production access', () => {
  it('lets an operator, admin or finance controller produce, and refuses a viewer', () => {
    expect(canProduceExport(['OPERATOR'])).toBe(true);
    expect(canProduceExport(['BUSINESS_ADMIN'])).toBe(true);
    expect(canProduceExport(['FINANCE_CONTROLLER'])).toBe(true);
    expect(canProduceExport(['VIEWER'])).toBe(false);
  });
});
