import { describe, it, expect } from 'vitest';
import {
  periodStateLabel,
  nextState,
  canDrivePeriod,
  canOperateCycle,
  canValidateFinance,
  paymentStatusLabel,
  batchTypeLabel,
  claimStatusLabel,
  severityTag,
  severityLabel,
  exclusionReasonLabel,
  decisionLabel,
} from './cycle';

describe('period state labels', () => {
  it('labels each known state in French and echoes an unknown one', () => {
    expect(periodStateLabel('OPEN')).toBe('Ouverte');
    expect(periodStateLabel('CLOSED')).toBe('Clôturée');
    expect(periodStateLabel('WAT')).toBe('WAT');
  });
});

describe('next state', () => {
  it('gives the forward successor and null at the end', () => {
    expect(nextState('OPEN')).toBe('LOADED');
    expect(nextState('PAID')).toBe('CLOSED');
    expect(nextState('CLOSED')).toBeNull();
  });
});

describe('cycle access', () => {
  it('lets a cycle role drive a transition and refuses a viewer', () => {
    expect(canDrivePeriod(['FINANCE_CONTROLLER'])).toBe(true);
    expect(canDrivePeriod(['VIEWER'])).toBe(false);
  });

  it('lets an operator or admin operate the cycle', () => {
    expect(canOperateCycle(['OPERATOR'])).toBe(true);
    expect(canOperateCycle(['BUSINESS_ADMIN'])).toBe(true);
    expect(canOperateCycle(['BUSINESS_VALIDATOR'])).toBe(false);
  });

  it('lets only the finance controller validate for finance', () => {
    expect(canValidateFinance(['FINANCE_CONTROLLER'])).toBe(true);
    expect(canValidateFinance(['OPERATOR'])).toBe(false);
  });
});

describe('payment status labels', () => {
  it('labels known statuses in French and echoes an unknown one', () => {
    expect(paymentStatusLabel('DRAFT')).toBe('Brouillon');
    expect(paymentStatusLabel('ISSUED')).toBe('Émis');
    expect(paymentStatusLabel('SETTLED')).toBe('Réglé');
    expect(paymentStatusLabel('TO_VALIDATE')).toBe('À valider');
    expect(paymentStatusLabel('REJECTED')).toBe('Rejeté');
    expect(paymentStatusLabel('CANCELLED')).toBe('Annulé');
    expect(paymentStatusLabel('VALIDATED')).toBe('Validé');
    expect(paymentStatusLabel('PUBLISHED')).toBe('Publié');
    expect(paymentStatusLabel('CORRECTED')).toBe('Rectifié');
    expect(paymentStatusLabel('XXX')).toBe('XXX');
  });

  it('labels known lot types in French and echoes an unknown one', () => {
    expect(batchTypeLabel('CYCLE')).toBe('Lot de cycle');
    expect(batchTypeLabel('SAVINGS_CYCLE')).toBe('Lot épargne retraite');
    expect(batchTypeLabel('OFF_CYCLE')).toBe('Lot hors cycle');
    expect(batchTypeLabel('CHEQUE')).toBe('Lot de chèques');
    expect(batchTypeLabel('XXX')).toBe('XXX');
  });

  it('labels claim statuses in French and echoes an unknown one', () => {
    expect(claimStatusLabel('OPEN')).toBe('Ouverte');
    expect(claimStatusLabel('IN_PROGRESS')).toBe('En cours');
    expect(claimStatusLabel('CLOSED')).toBe('Close');
    expect(claimStatusLabel('XXX')).toBe('XXX');
  });
});

describe('anomaly presentation', () => {
  it('classifies each severity', () => {
    expect(severityTag('BLOCKING')).toBe('tag ko');
    expect(severityTag('WARNING')).toBe('tag warn');
    expect(severityTag('INFO')).toBe('tag mute');
  });

  it('labels each decision', () => {
    expect(decisionLabel('OPEN')).toBe('Ouverte');
    expect(decisionLabel('FIXED')).toBe('Corrigée');
    expect(decisionLabel('ACCEPTED')).toBe('Acceptée');
    expect(decisionLabel('DEFERRED')).toBe('Reportée');
  });

  it('labels each severity in French and echoes an unknown one', () => {
    expect(severityLabel('BLOCKING')).toBe('Bloquant');
    expect(severityLabel('WARNING')).toBe('Avertissement');
    expect(severityLabel('INFO')).toBe('Information');
    expect(severityLabel('XXX')).toBe('XXX');
  });

  it('labels each payment-exclusion reason in French and echoes an unknown one', () => {
    expect(exclusionReasonLabel('ACCOUNT_BLOCKED')).toBe('Compte bloqué');
    expect(exclusionReasonLabel('BELOW_THRESHOLD')).toBe('Sous le seuil de paiement');
    expect(exclusionReasonLabel('FREQUENCY')).toBe('Hors fréquence de versement');
    expect(exclusionReasonLabel('NO_BANK_DETAILS')).toBe('Sans coordonnées bancaires');
    expect(exclusionReasonLabel('NON_POSITIVE_BALANCE')).toBe('Solde non positif');
    expect(exclusionReasonLabel('PAYEE_INACTIVE')).toBe('Bénéficiaire inactif');
    expect(exclusionReasonLabel('XXX')).toBe('XXX');
  });
});
