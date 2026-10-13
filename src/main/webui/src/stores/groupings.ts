import { defineStore } from 'pinia';
import { apiGet } from '../api/client';

interface GroupView {
  code: string;
  label: string;
}

/**
 * Catalogue of distributor groupings, loaded once from {@code /groups}. It feeds the grouping
 * selector so an operator picks a grouping by its name from a list instead of typing a code
 * (ADM-03). A single load serves every screen; the endpoint already partitions by entity.
 */
export const useGroupingsStore = defineStore('groupings', {
  state: () => ({
    /** Every grouping the user may see, by code and label. */
    list: [] as GroupView[],
    /** Whether the catalogue has been loaded (success or failure). */
    loaded: false,
  }),
  getters: {
    /** The groupings as (code, "label (code)") options. */
    options: (state): ReadonlyArray<{ value: string; label: string }> =>
      state.list.map((g) => ({ value: g.code, label: `${g.label} (${g.code})` })),
  },
  actions: {
    /**
     * Loads the grouping catalogue once. A second call is a no-op. On failure the catalogue stays
     * empty but is marked loaded, so the selector degrades to the caller's current value.
     */
    async ensure(): Promise<void> {
      if (this.loaded) {
        return;
      }
      try {
        this.list = await apiGet<GroupView[]>('/api/ui/v1/groups');
      } catch {
        this.list = [];
      } finally {
        this.loaded = true;
      }
    },
  },
});
