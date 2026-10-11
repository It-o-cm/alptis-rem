import { describe, it, expect } from 'vitest';
import { flowLabel } from './imports';

describe('flow labels', () => {
  it('labels every pivot flow code in French', () => {
    expect(flowLabel('events')).toBe('Événements');
    expect(flowLabel('coverages')).toBe('Garanties');
    expect(flowLabel('distributors')).toBe('Distributeurs');
    expect(flowLabel('distributor_groups')).toBe('Groupements');
    expect(flowLabel('production_codes')).toBe('Codes de production');
    expect(flowLabel('insurers')).toBe('Assureurs');
    expect(flowLabel('product_ranges')).toBe('Gammes');
    expect(flowLabel('products')).toBe('Produits');
    expect(flowLabel('product_options')).toBe('Options de produit');
    expect(flowLabel('bank_accounts')).toBe('Coordonnées bancaires');
    expect(flowLabel('quality_indicators')).toBe('Indicateurs de qualité');
    expect(flowLabel('portfolio_transfers')).toBe('Cessions de portefeuille');
    expect(flowLabel('replacement_orders')).toBe('Ordres de remplacement');
    expect(flowLabel('payee_rules')).toBe('Règles de versement');
    expect(flowLabel('external_codes')).toBe('Codes externes');
  });

  it('echoes an unknown code', () => {
    expect(flowLabel('mystery')).toBe('mystery');
  });
});
