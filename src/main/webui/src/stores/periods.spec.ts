import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { usePeriodsStore } from './periods';

function okResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function errorResponse(status: number) {
  return { ok: false, status, json: async () => ({ detail: 'refus' }) } as unknown as Response;
}

describe('periods store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts empty and unloaded', () => {
    const periods = usePeriodsStore();
    expect(periods.loaded).toBe(false);
    expect(periods.monthsFor()).toEqual([]);
  });

  it('loads the catalogue and lists distinct months, most recent first', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        // The server returns each month as the first-of-month ISO date (section 9.8), newest first.
        okResponse([
          { entityCode: 'ALPTIS_ASSURANCES', month: '2026-08-01' },
          { entityCode: 'ALPTIS_ASSURANCES', month: '2026-09-01' },
          { entityCode: 'UNEP', month: '2026-09-01' },
          { entityCode: 'UNEP', month: '2026-07-01' },
        ]),
      ),
    );
    const periods = usePeriodsStore();
    await periods.ensure();

    expect(periods.loaded).toBe(true);
    expect(periods.monthsFor()).toEqual(['2026-09-01', '2026-08-01', '2026-07-01']);
    expect(periods.monthsFor('ALPTIS_ASSURANCES')).toEqual(['2026-09-01', '2026-08-01']);
    expect(periods.monthsFor('UNEP')).toEqual(['2026-09-01', '2026-07-01']);
  });

  it('loads only once', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const periods = usePeriodsStore();
    await periods.ensure();
    await periods.ensure();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('degrades to an empty catalogue when the load fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(403)));
    const periods = usePeriodsStore();
    await periods.ensure();
    expect(periods.loaded).toBe(true);
    expect(periods.monthsFor()).toEqual([]);
  });
});
