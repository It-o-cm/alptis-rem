import { describe, it, expect } from 'vitest';
import {
  periodStateLabel,
  nextState,
  canDrivePeriod,
  canOperateCycle,
  severityTag,
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
});
