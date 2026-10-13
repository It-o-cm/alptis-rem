import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useRulesStore } from './rules';

function okResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function errorResponse(status: number) {
  return { ok: false, status, json: async () => ({ detail: 'refus' }) } as unknown as Response;
}

const SAMPLE = [
  { entityCode: 'ALPTIS_ASSURANCES', planCode: 'PLAN-A', code: 'R1', label: 'Linéaire' },
  { entityCode: 'ALPTIS_ASSURANCES', planCode: 'PLAN-A', code: 'R2', label: 'Escompte' },
  { entityCode: 'ALPTIS_ASSURANCES', planCode: 'PLAN-B', code: 'R3', label: 'Reprise' },
];

describe('rules store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts empty and unloaded', () => {
    const rules = useRulesStore();
    expect(rules.loaded).toBe(false);
    expect(rules.options).toEqual([]);
    expect(rules.optionsFor('PLAN-A')).toEqual([]);
  });

  it('loads the catalogue and offers all options and per-plan options', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(okResponse(SAMPLE)));
    const rules = useRulesStore();
    await rules.ensure();

    expect(rules.loaded).toBe(true);
    expect(rules.options).toEqual([
      { value: 'R1', label: 'Linéaire (R1)' },
      { value: 'R2', label: 'Escompte (R2)' },
      { value: 'R3', label: 'Reprise (R3)' },
    ]);
    // Filtered to one plan.
    expect(rules.optionsFor('PLAN-A')).toEqual([
      { value: 'R1', label: 'Linéaire (R1)' },
      { value: 'R2', label: 'Escompte (R2)' },
    ]);
    // No plan given: every rule.
    expect(rules.optionsFor()).toHaveLength(3);
  });

  it('loads only once', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const rules = useRulesStore();
    await rules.ensure();
    await rules.ensure();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('degrades to an empty catalogue when the load fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(403)));
    const rules = useRulesStore();
    await rules.ensure();
    expect(rules.loaded).toBe(true);
    expect(rules.options).toEqual([]);
  });
});
