import { defineStore } from 'pinia';
import { apiGet } from '../api/client';

/**
 * Catalogue of source systems, loaded once from {@code /imports/source-systems}. It feeds the source
 * system selector so an operator picks the system from a list instead of typing a code (ADM-03). A
 * single load serves every screen; the endpoint already partitions by entity.
 */
export const useSourceSystemsStore = defineStore('sourceSystems', {
  state: () => ({
    /** Every distinct source system the user may see. */
    list: [] as string[],
    /** Whether the catalogue has been loaded (success or failure). */
    loaded: false,
  }),
  getters: {
    /** The source systems as (value, label) options, the value being the system code itself. */
    options: (state): ReadonlyArray<{ value: string; label: string }> =>
      state.list.map((s) => ({ value: s, label: s })),
  },
  actions: {
    /**
     * Loads the source system catalogue once. A second call is a no-op. On failure the catalogue
     * stays empty but is marked loaded, so the selector degrades to the caller's current value.
     */
    async ensure(): Promise<void> {
      if (this.loaded) {
        return;
      }
      try {
        this.list = await apiGet<string[]>('/api/ui/v1/imports/source-systems');
      } catch {
        this.list = [];
      } finally {
        this.loaded = true;
      }
    },
  },
});
