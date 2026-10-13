import { defineStore } from 'pinia';
import { apiGet } from '../api/client';

interface RuleOption {
  entityCode: string;
  planCode: string;
  code: string;
  label: string;
}

/**
 * Catalogue of rules, loaded once from {@code /rules}. It feeds the rule selector so an operator
 * picks a rule by its name from a list instead of typing a code (ADM-03). A single load serves every
 * screen; the endpoint already partitions by entity. The selector may restrict the list to one plan.
 */
export const useRulesStore = defineStore('rules', {
  state: () => ({
    /** Every rule the user may see, with its plan and entity. */
    list: [] as RuleOption[],
    /** Whether the catalogue has been loaded (success or failure). */
    loaded: false,
  }),
  getters: {
    /** The rules as (code, "label (code)") options. */
    options: (state): ReadonlyArray<{ value: string; label: string }> =>
      state.list.map((r) => ({ value: r.code, label: `${r.label} (${r.code})` })),
    /** Builds the (code, "label (code)") options of a single plan, or all rules when no plan is given. */
    optionsFor:
      (state) =>
      (planCode?: string): ReadonlyArray<{ value: string; label: string }> =>
        state.list
          .filter((r) => !planCode || r.planCode === planCode)
          .map((r) => ({ value: r.code, label: `${r.label} (${r.code})` })),
  },
  actions: {
    /**
     * Loads the rule catalogue once. A second call is a no-op. On failure the catalogue stays empty
     * but is marked loaded, so the selector degrades to the caller's current value.
     */
    async ensure(): Promise<void> {
      if (this.loaded) {
        return;
      }
      try {
        this.list = await apiGet<RuleOption[]>('/api/ui/v1/rules');
      } catch {
        this.list = [];
      } finally {
        this.loaded = true;
      }
    },
  },
});
