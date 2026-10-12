import { describe, it, expect } from 'vitest';
import { runTypeLabel, runStatusLabel, runStatusTag, RECALC_SCOPES } from './calc';

describe('calculation labels', () => {
  it('labels run types in French and echoes an unknown one', () => {
    expect(runTypeLabel('ACTUAL')).toBe('Réel');
    expect(runTypeLabel('FORECAST')).toBe('Prévisionnel');
    expect(runTypeLabel('SIMULATION')).toBe('Simulation');
    expect(runTypeLabel('XXX')).toBe('XXX');
  });

  it('labels run statuses in French and echoes an unknown one', () => {
    expect(runStatusLabel('RUNNING')).toBe('En cours');
    expect(runStatusLabel('COMPLETED')).toBe('Terminé');
    expect(runStatusLabel('FAILED')).toBe('En échec');
    expect(runStatusLabel('XXX')).toBe('XXX');
  });

  it('classifies each run status into a tag', () => {
    expect(runStatusTag('COMPLETED')).toBe('tag ok');
    expect(runStatusTag('FAILED')).toBe('tag ko');
    expect(runStatusTag('RUNNING')).toBe('tag mute');
  });

  it('offers the three recalc scopes', () => {
    expect(RECALC_SCOPES.map((s) => s.value)).toEqual(['DISTRIBUTOR', 'PRODUCT', 'RULE']);
  });
});
