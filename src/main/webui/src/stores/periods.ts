import { defineStore } from 'pinia';
import { apiGet } from '../api/client';

interface PeriodSummary {
  entityCode: string;
  month: string;
}

/**
 * Catalogue of the accounting months the system knows, loaded once from {@code /periods}. It feeds
 * the month selector so an operator picks a month from a list instead of typing an ISO code
 * (ADM-03). The list is read-only reference data; a single load serves every screen.
 */
export const usePeriodsStore = defineStore('periods', {
  state: () => ({
    /** Every (entity, month) period returned by the server. */
    periods: [] as PeriodSummary[],
    /** Whether the catalogue has been loaded (success or failure). */
    loaded: false,
  }),
  getters: {
    /**
     * The known months, most recent first, optionally restricted to one entity. Returns a function
     * so a caller passes the entity it is scoped to.
     */
    monthsFor:
      (state) =>
      (entity?: string): string[] => {
        const months = state.periods
          .filter((p) => !entity || p.entityCode === entity)
          .map((p) => p.month);
        return [...new Set(months)].sort().reverse();
      },
  },
  actions: {
    /**
     * Loads the period catalogue once. A second call is a no-op. On failure the catalogue stays
     * empty but is marked loaded, so the selector degrades to the caller's current value.
     */
    async ensure(): Promise<void> {
      if (this.loaded) {
        return;
      }
      try {
        this.periods = await apiGet<PeriodSummary[]>('/api/ui/v1/periods');
      } catch {
        this.periods = [];
      } finally {
        this.loaded = true;
      }
    },
  },
});
