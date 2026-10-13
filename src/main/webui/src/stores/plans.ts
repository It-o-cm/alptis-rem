import { defineStore } from 'pinia';
import { apiGet } from '../api/client';

interface PlanView {
  entityCode: string;
  code: string;
  label: string;
}

/**
 * Catalogue of commission plans, loaded once from {@code /plans}. It feeds the plan selector so an
 * operator picks a plan by its name from a list instead of typing a code (ADM-03). A single load
 * serves every screen; the endpoint already partitions by entity.
 */
export const usePlansStore = defineStore('plans', {
  state: () => ({
    /** Every plan the user may see, by entity, code and label. */
    list: [] as PlanView[],
    /** Whether the catalogue has been loaded (success or failure). */
    loaded: false,
  }),
  getters: {
    /** The plans as (code, "label (code)") options. */
    options: (state): ReadonlyArray<{ value: string; label: string }> =>
      state.list.map((p) => ({ value: p.code, label: `${p.label} (${p.code})` })),
  },
  actions: {
    /**
     * Loads the plan catalogue once. A second call is a no-op. On failure the catalogue stays empty
     * but is marked loaded, so the selector degrades to the caller's current value.
     */
    async ensure(): Promise<void> {
      if (this.loaded) {
        return;
      }
      try {
        this.list = await apiGet<PlanView[]>('/api/ui/v1/plans');
      } catch {
        this.list = [];
      } finally {
        this.loaded = true;
      }
    },
  },
});
