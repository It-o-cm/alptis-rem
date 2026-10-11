import { describe, it, expect } from 'vitest';
import {
  eventStatusLabel,
  holdReasonLabel,
  eventTypeLabel,
  STATUS_OPTIONS,
  EVENT_TYPE_OPTIONS,
} from './events';

describe('event labels', () => {
  it('labels statuses in French and echoes an unknown one', () => {
    expect(eventStatusLabel('ON_HOLD')).toBe('Mis à l’écart');
    expect(eventStatusLabel('PROCESSED')).toBe('Traité');
    expect(eventStatusLabel('PENDING')).toBe('En attente de calcul');
    expect(eventStatusLabel('NOT_COMMISSIONABLE')).toBe('Non commissionnable');
    expect(eventStatusLabel('XXX')).toBe('XXX');
  });

  it('labels hold reasons, a dash for none, and echoes an unknown one', () => {
    expect(holdReasonLabel('UNKNOWN_REFERENCE')).toBe('Référence inconnue');
    expect(holdReasonLabel('NO_RULE')).toBe('Aucune règle applicable');
    expect(holdReasonLabel(null)).toBe('—');
    expect(holdReasonLabel('XXX')).toBe('XXX');
  });

  it('labels event types in French and echoes an unknown one', () => {
    expect(eventTypeLabel('PREMIUM_COLLECTED')).toBe('Prime encaissée');
    expect(eventTypeLabel('SCHEDULED_PAYMENT')).toBe('Versement programmé');
    expect(eventTypeLabel('TERMINATION')).toBe('Résiliation');
    expect(eventTypeLabel('XXX')).toBe('XXX');
  });

  it('exposes labelled filter options', () => {
    expect(STATUS_OPTIONS.find((o) => o.value === 'ON_HOLD')?.label).toBe('Mis à l’écart');
    expect(EVENT_TYPE_OPTIONS.find((o) => o.value === 'TERMINATION')?.label).toBe('Résiliation');
    expect(STATUS_OPTIONS.length).toBeGreaterThan(0);
    expect(EVENT_TYPE_OPTIONS.length).toBeGreaterThan(0);
  });
});
