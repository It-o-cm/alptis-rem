import { defineStore } from 'pinia';
import { apiGet } from '../api/client';

interface ProductView {
  code: string;
  label: string;
}

/**
 * Catalogue of products, loaded once from {@code /products}. It feeds the product selector so an
 * operator picks a product by its name from a list instead of typing a code (ADM-03). A single load
 * serves every screen; the endpoint already partitions by entity.
 */
export const useProductsStore = defineStore('products', {
  state: () => ({
    /** Every product the user may see, by code and label. */
    list: [] as ProductView[],
    /** Whether the catalogue has been loaded (success or failure). */
    loaded: false,
  }),
  getters: {
    /** The products as (code, "label (code)") options. */
    options: (state): ReadonlyArray<{ value: string; label: string }> =>
      state.list.map((p) => ({ value: p.code, label: `${p.label} (${p.code})` })),
  },
  actions: {
    /**
     * Loads the product catalogue once. A second call is a no-op. On failure the catalogue stays
     * empty but is marked loaded, so the selector degrades to the caller's current value.
     */
    async ensure(): Promise<void> {
      if (this.loaded) {
        return;
      }
      try {
        this.list = await apiGet<ProductView[]>('/api/ui/v1/products');
      } catch {
        this.list = [];
      } finally {
        this.loaded = true;
      }
    },
  },
});
