import { describe, it, expect } from 'vitest';
import { accountingEventLabel, accountingVariantLabel, accountRoleLabel } from './accounting';

describe('accounting labels', () => {
  it('labels accounting events, a dash for none, and echoes an unknown one', () => {
    expect(accountingEventLabel('FORECAST_RECOGNITION')).toBe('Constatation du prévisionnel');
    expect(accountingEventLabel('EARNING')).toBe('Acquisition');
    expect(accountingEventLabel('PAYMENT')).toBe('Paiement');
    expect(accountingEventLabel('ADVANCE_WRITE_OFF')).toBe('Abandon de reliquat d’escompte');
    expect(accountingEventLabel('INSURER_RECEIVABLE')).toBe('Créance assureur');
    expect(accountingEventLabel(null)).toBe('—');
    expect(accountingEventLabel('XXX')).toBe('XXX');
  });

  it('labels scheme variants, a dash for none, and echoes an unknown one', () => {
    expect(accountingVariantLabel('STANDARD')).toBe('Standard');
    expect(accountingVariantLabel('NEGATIVE')).toBe('Montant négatif');
    expect(accountingVariantLabel('NO_FORECAST')).toBe('Sans prévisionnel');
    expect(accountingVariantLabel('SAVINGS')).toBe('Épargne retraite');
    expect(accountingVariantLabel(null)).toBe('—');
    expect(accountingVariantLabel('XXX')).toBe('XXX');
  });

  it('labels account roles, a dash for none, and echoes an unknown one', () => {
    expect(accountRoleLabel('FORECAST_EXPENSE')).toBe('Charge de commission prévisionnelle');
    expect(accountRoleLabel('DISTRIBUTOR_PAYABLE')).toBe('Distributeur, commissions à payer');
    expect(accountRoleLabel('BANK')).toBe('Banque');
    expect(accountRoleLabel('INSURER_RECEIVABLE')).toBe('Assureur, commissions à recevoir');
    expect(accountRoleLabel('COMMISSION_INCOME')).toBe('Produits de commissions');
    expect(accountRoleLabel(null)).toBe('—');
    expect(accountRoleLabel('XXX')).toBe('XXX');
  });
});
