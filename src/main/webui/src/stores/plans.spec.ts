import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { usePlansStore } from './plans';

function okResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function errorResponse(status: number) {
  return { ok: false, status, json: async () => ({ detail: 'refus' }) } as unknown as Response;
}

describe('plans store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts empty and unloaded', () => {
    const plans = usePlansStore();
    expect(plans.loaded).toBe(false);
    expect(plans.options).toEqual([]);
  });

  it('loads the catalogue and offers label-first options', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        okResponse([
          { entityCode: 'ALPTIS_ASSURANCES', code: 'PLAN-SANTE', label: 'Plan Santé' },
          { entityCode: 'UNEP', code: 'PLAN-PREV', label: 'Plan Prévoyance' },
        ]),
      ),
    );
    const plans = usePlansStore();
    await plans.ensure();

    expect(plans.loaded).toBe(true);
    expect(plans.options).toEqual([
      { value: 'PLAN-SANTE', label: 'Plan Santé (PLAN-SANTE)' },
      { value: 'PLAN-PREV', label: 'Plan Prévoyance (PLAN-PREV)' },
    ]);
  });

  it('loads only once', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const plans = usePlansStore();
    await plans.ensure();
    await plans.ensure();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('degrades to an empty catalogue when the load fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(403)));
    const plans = usePlansStore();
    await plans.ensure();
    expect(plans.loaded).toBe(true);
    expect(plans.options).toEqual([]);
  });
});
