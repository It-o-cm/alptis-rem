import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useProductionCodesStore } from './productionCodes';

function okResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function errorResponse(status: number) {
  return { ok: false, status, json: async () => ({ detail: 'refus' }) } as unknown as Response;
}

describe('productionCodes store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts empty and unloaded', () => {
    const codes = useProductionCodesStore();
    expect(codes.loaded).toBe(false);
    expect(codes.options).toEqual([]);
  });

  it('loads the catalogue, labelling with the distributor when known and the bare code otherwise', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        okResponse([
          { entityCode: 'ALPTIS_ASSURANCES', code: 'PC-CREDIT', distributorName: 'Cabinet Martin' },
          { entityCode: 'ALPTIS_ASSURANCES', code: 'PC-ORPHAN', distributorName: null },
        ]),
      ),
    );
    const codes = useProductionCodesStore();
    await codes.ensure();

    expect(codes.loaded).toBe(true);
    expect(codes.options).toEqual([
      { value: 'PC-CREDIT', label: 'PC-CREDIT — Cabinet Martin' },
      { value: 'PC-ORPHAN', label: 'PC-ORPHAN' },
    ]);
  });

  it('loads only once', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const codes = useProductionCodesStore();
    await codes.ensure();
    await codes.ensure();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('degrades to an empty catalogue when the load fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(403)));
    const codes = useProductionCodesStore();
    await codes.ensure();
    expect(codes.loaded).toBe(true);
    expect(codes.options).toEqual([]);
  });
});
