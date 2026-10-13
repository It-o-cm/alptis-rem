import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useDistributorsStore } from './distributors';

function okResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function errorResponse(status: number) {
  return { ok: false, status, json: async () => ({ detail: 'refus' }) } as unknown as Response;
}

describe('distributors store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts empty and unloaded', () => {
    const distributors = useDistributorsStore();
    expect(distributors.loaded).toBe(false);
    expect(distributors.options).toEqual([]);
  });

  it('loads the catalogue and offers name-first options', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        okResponse({
          items: [
            { partnerCode: 'DIST-001', legalName: 'Cabinet Martin' },
            { partnerCode: 'DIST-002', legalName: 'Courtage Durand' },
          ],
        }),
      ),
    );
    const distributors = useDistributorsStore();
    await distributors.ensure();

    expect(distributors.loaded).toBe(true);
    expect(distributors.options).toEqual([
      { value: 'DIST-001', label: 'Cabinet Martin (DIST-001)' },
      { value: 'DIST-002', label: 'Courtage Durand (DIST-002)' },
    ]);
  });

  it('loads only once', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse({ items: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const distributors = useDistributorsStore();
    await distributors.ensure();
    await distributors.ensure();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('degrades to an empty catalogue when the load fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(403)));
    const distributors = useDistributorsStore();
    await distributors.ensure();
    expect(distributors.loaded).toBe(true);
    expect(distributors.options).toEqual([]);
  });
});
