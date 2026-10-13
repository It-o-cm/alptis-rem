import { defineStore } from 'pinia';
import { apiGet } from '../api/client';

interface ProductionCodeView {
  entityCode: string;
  code: string;
  distributorName: string | null;
}

/**
 * Catalogue of production codes, loaded once from {@code /production-codes}. It feeds the member
 * selector of the informative statement (BRD-11) so an operator picks a production code from a list
 * instead of typing it (ADM-03). A single load serves every screen; the endpoint partitions by
 * entity.
 */
export const useProductionCodesStore = defineStore('productionCodes', {
  state: () => ({
    /** Every production code the user may see, with its distributor. */
    list: [] as ProductionCodeView[],
    /** Whether the catalogue has been loaded (success or failure). */
    loaded: false,
  }),
  getters: {
    /** The production codes as (code, "code — distributor") options, falling back to the bare code. */
    options: (state): ReadonlyArray<{ value: string; label: string }> =>
      state.list.map((p) => ({
        value: p.code,
        label: p.distributorName ? `${p.code} — ${p.distributorName}` : p.code,
      })),
  },
  actions: {
    /**
     * Loads the production-code catalogue once. A second call is a no-op. On failure the catalogue
     * stays empty but is marked loaded, so the selector degrades to the caller's current value.
     */
    async ensure(): Promise<void> {
      if (this.loaded) {
        return;
      }
      try {
        this.list = await apiGet<ProductionCodeView[]>('/api/ui/v1/production-codes');
      } catch {
        this.list = [];
      } finally {
        this.loaded = true;
      }
    },
  },
});
