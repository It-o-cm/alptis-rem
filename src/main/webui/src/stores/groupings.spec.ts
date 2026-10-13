import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useGroupingsStore } from './groupings';

function okResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function errorResponse(status: number) {
  return { ok: false, status, json: async () => ({ detail: 'refus' }) } as unknown as Response;
}

describe('groupings store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts empty and unloaded', () => {
    const groupings = useGroupingsStore();
    expect(groupings.loaded).toBe(false);
    expect(groupings.options).toEqual([]);
  });

  it('loads the catalogue and offers label-first options', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        okResponse([
          { code: 'GRP-SUD', label: 'Groupement Sud' },
          { code: 'GRP-NORD', label: 'Groupement Nord' },
        ]),
      ),
    );
    const groupings = useGroupingsStore();
    await groupings.ensure();

    expect(groupings.loaded).toBe(true);
    expect(groupings.options).toEqual([
      { value: 'GRP-SUD', label: 'Groupement Sud (GRP-SUD)' },
      { value: 'GRP-NORD', label: 'Groupement Nord (GRP-NORD)' },
    ]);
  });

  it('loads only once', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const groupings = useGroupingsStore();
    await groupings.ensure();
    await groupings.ensure();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('degrades to an empty catalogue when the load fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(403)));
    const groupings = useGroupingsStore();
    await groupings.ensure();
    expect(groupings.loaded).toBe(true);
    expect(groupings.options).toEqual([]);
  });
});
