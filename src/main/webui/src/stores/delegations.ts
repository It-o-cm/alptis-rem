import { defineStore } from 'pinia';
import { apiGet } from '../api/client';

/**
 * Catalogue of sales delegations, loaded once from {@code /delegations}. It feeds the delegation
 * selector so an operator picks a delegation from a list instead of typing one (ADM-03). A single
 * load serves every screen; the endpoint already partitions by entity.
 */
export const useDelegationsStore = defineStore('delegations', {
  state: () => ({
    /** Every distinct sales delegation the user may see. */
    list: [] as string[],
    /** Whether the catalogue has been loaded (success or failure). */
    loaded: false,
  }),
  getters: {
    /** The delegations as (value, label) options, the value being the delegation itself. */
    options: (state): ReadonlyArray<{ value: string; label: string }> =>
      state.list.map((d) => ({ value: d, label: d })),
  },
  actions: {
    /**
     * Loads the delegation catalogue once. A second call is a no-op. On failure the catalogue stays
     * empty but is marked loaded, so the selector degrades to the caller's current value.
     */
    async ensure(): Promise<void> {
      if (this.loaded) {
        return;
      }
      try {
        this.list = await apiGet<string[]>('/api/ui/v1/delegations');
      } catch {
        this.list = [];
      } finally {
        this.loaded = true;
      }
    },
  },
});
