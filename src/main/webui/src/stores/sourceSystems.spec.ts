import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useSourceSystemsStore } from './sourceSystems';

function okResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function errorResponse(status: number) {
  return { ok: false, status, json: async () => ({ detail: 'refus' }) } as unknown as Response;
}

describe('source systems store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts empty and unloaded', () => {
    const systems = useSourceSystemsStore();
    expect(systems.loaded).toBe(false);
    expect(systems.options).toEqual([]);
  });

  it('loads the catalogue and offers each system as its own value', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(okResponse(['CLEVA', 'SESAME'])));
    const systems = useSourceSystemsStore();
    await systems.ensure();

    expect(systems.loaded).toBe(true);
    expect(systems.options).toEqual([
      { value: 'CLEVA', label: 'CLEVA' },
      { value: 'SESAME', label: 'SESAME' },
    ]);
  });

  it('loads only once', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const systems = useSourceSystemsStore();
    await systems.ensure();
    await systems.ensure();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('degrades to an empty catalogue when the load fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(403)));
    const systems = useSourceSystemsStore();
    await systems.ensure();
    expect(systems.loaded).toBe(true);
    expect(systems.options).toEqual([]);
  });
});
