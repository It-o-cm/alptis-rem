import { defineStore } from 'pinia';
import { apiGet } from '../api/client';

interface DistributorView {
  partnerCode: string;
  legalName: string;
}

interface Page {
  items: DistributorView[];
}

/**
 * Catalogue of distributors, loaded once from the search endpoint. It feeds the distributor
 * selector so an operator picks a distributor by name from a list instead of typing a partner code
 * (ADM-03). A single load serves every screen; the search endpoint already partitions by entity.
 */
export const useDistributorsStore = defineStore('distributors', {
  state: () => ({
    /** Every distributor the user may see, by partner code and legal name. */
    list: [] as DistributorView[],
    /** Whether the catalogue has been loaded (success or failure). */
    loaded: false,
  }),
  getters: {
    /** The distributors as (code, "name (code)") options, name first for readability. */
    options: (state): ReadonlyArray<{ value: string; label: string }> =>
      state.list.map((d) => ({ value: d.partnerCode, label: `${d.legalName} (${d.partnerCode})` })),
  },
  actions: {
    /**
     * Loads the distributor catalogue once. A second call is a no-op. On failure the catalogue stays
     * empty but is marked loaded, so the selector degrades to the caller's current value.
     */
    async ensure(): Promise<void> {
      if (this.loaded) {
        return;
      }
      try {
        const page = await apiGet<Page>('/api/ui/v1/distributors?q=&page=0&size=500');
        this.list = page.items;
      } catch {
        this.list = [];
      } finally {
        this.loaded = true;
      }
    },
  },
});
