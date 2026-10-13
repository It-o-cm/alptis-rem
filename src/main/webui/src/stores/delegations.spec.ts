import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useDelegationsStore } from './delegations';

function okResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function errorResponse(status: number) {
  return { ok: false, status, json: async () => ({ detail: 'refus' }) } as unknown as Response;
}

describe('delegations store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts empty and unloaded', () => {
    const delegations = useDelegationsStore();
    expect(delegations.loaded).toBe(false);
    expect(delegations.options).toEqual([]);
  });

  it('loads the catalogue and offers each delegation as its own value', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(okResponse(['NORD', 'SUD-EST'])));
    const delegations = useDelegationsStore();
    await delegations.ensure();

    expect(delegations.loaded).toBe(true);
    expect(delegations.options).toEqual([
      { value: 'NORD', label: 'NORD' },
      { value: 'SUD-EST', label: 'SUD-EST' },
    ]);
  });

  it('loads only once', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const delegations = useDelegationsStore();
    await delegations.ensure();
    await delegations.ensure();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('degrades to an empty catalogue when the load fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(403)));
    const delegations = useDelegationsStore();
    await delegations.ensure();
    expect(delegations.loaded).toBe(true);
    expect(delegations.options).toEqual([]);
  });
});
