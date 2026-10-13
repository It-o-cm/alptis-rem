<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount, formatPercent } from '../presentation/format';
import { AppButton, AppCard, AppMessage, DataTable, EntitySelect, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';
import LineChart from '../components/charts/LineChart.vue';

interface IndicatorMonth {
  month: string;
  activeRules: number;
  cycleDuration: number;
  manualShare: number;
  clawback: number;
}
interface Indicators {
  entity: string;
  months: string[];
  rows: IndicatorMonth[];
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const from = ref('');
const to = ref('');
const data = ref<Indicators | null>(null);
const csv = ref('');
const error = ref('');
const loading = ref(false);

/** Builds the query string from the current month-range values. */
function query(): string {
  const params = new URLSearchParams();
  if (from.value) {
    params.set('from', from.value);
  }
  if (to.value) {
    params.set('to', to.value);
  }
  const q = params.toString();
  return q ? `?${q}` : '';
}

/** Loads the operating indicators of the entity over the filtered range. */
async function load(): Promise<void> {
  error.value = '';
  csv.value = '';
  loading.value = true;
  try {
    data.value = await apiGet<Indicators>(`/api/ui/v1/reporting/${entity.value}/indicators${query()}`);
  } catch (e) {
    data.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/** Fetches the CSV export of the indicators for display. */
async function exportCsv(): Promise<void> {
  csv.value = await apiGetText(`/api/ui/v1/reporting/${entity.value}/export/indicators${query()}`);
}

/** The months covered by the loaded indicators. */
const months = computed(() => data.value?.months ?? []);

/**
 * Builds a single-value line series over the indicator rows.
 * @param label the series label
 * @param pick the accessor of the plotted value
 */
function serie(label: string, pick: (r: IndicatorMonth) => number) {
  return [{ key: 'v', label, values: data.value ? data.value.rows.map(pick) : [] }];
}
/** Line series of active rules per month. */
const rulesSeries = computed(() => serie('Règles actives', (r) => r.activeRules));
/** Line series of the cycle duration per month. */
const cycleSeries = computed(() => serie('Durée du cycle', (r) => r.cycleDuration));
/** Line series of the manual-line share per month. */
const manualSeries = computed(() => serie('Lignes manuelles', (r) => r.manualShare));
/** Line series of clawed-back amounts per month. */
const clawbackSeries = computed(() => serie('Montants repris', (r) => r.clawback));

/** Indicator rows paired with their index, for the per-row test identifier. */
const indexedRows = computed(() => (data.value ? data.value.rows.map((r, i) => ({ ...r, i })) : []));

/** Columns of the indicators table. */
const columns: Column<IndicatorMonth & { i: number }>[] = [
  { key: 'month', label: 'Mois' },
  { key: 'activeRules', label: 'Règles actives', align: 'num' },
  { key: 'cycleDuration', label: 'Durée du cycle (jours)', align: 'num' },
  { key: 'manualShare', label: 'Lignes manuelles', align: 'num', value: (r) => formatPercent(r.manualShare) },
  { key: 'clawback', label: 'Montants repris', align: 'num', value: (r) => formatAmount(r.clawback) },
];

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading title="Indicateurs de fonctionnement" subtitle="Règles actives, durée du cycle, part des lignes ajustées à la main et montants repris, mois par mois (REP-09)." />

    <FilterBar
      :loading="loading"
      search-label="Rafraîchir"
      search-testid="ind-refresh"
      :resettable="false"
      @search="load"
    >
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="ind-entity" v-model="entity" />
      </FormField>
      <FormField label="Du mois" stacked width="date" v-slot="{ id }">
        <input :id="id" data-testid="ind-from" type="text" v-model="from" placeholder="2026-06-01" />
      </FormField>
      <FormField label="Au mois" stacked width="date" v-slot="{ id }">
        <input :id="id" data-testid="ind-to" type="text" v-model="to" placeholder="2026-11-01" />
      </FormField>
    </FilterBar>

    <div class="ind-actions">
      <AppButton variant="secondary" data-testid="ind-export" @click="exportCsv">Exporter CSV</AppButton>
    </div>

    <AppMessage v-if="error" variant="error" data-testid="ind-error">{{ error }}</AppMessage>

    <AppCard v-if="csv">
      <pre data-testid="ind-export-content" class="ind-csv">{{ csv }}</pre>
    </AppCard>

    <div v-if="data" class="ind-charts">
      <AppCard>
        <LineChart :categories="months" :series="rulesSeries" title="Règles actives par mois" testid="chart-ind-rules" />
      </AppCard>
      <AppCard>
        <LineChart :categories="months" :series="cycleSeries" title="Durée du cycle (jours) par mois" testid="chart-ind-cycle" />
      </AppCard>
      <AppCard>
        <LineChart :categories="months" :series="manualSeries" title="Part des lignes manuelles par mois" testid="chart-ind-manual" />
      </AppCard>
      <AppCard>
        <LineChart :categories="months" :series="clawbackSeries" title="Montants repris par mois" testid="chart-ind-clawback" />
      </AppCard>
    </div>

    <AppCard v-if="data" flush>
      <DataTable
        :columns="columns"
        :rows="indexedRows"
        empty="Aucune donnée."
        empty-testid="ind-empty"
        :row-testid="(r) => `ind-row-${r.i}`"
      >
        <template #cell-activeRules="{ row }">
          <span :data-testid="`ind-rules-${row.i}`">{{ row.activeRules }}</span>
        </template>
        <template #cell-clawback="{ row }">
          <span :data-testid="`ind-clawback-${row.i}`">{{ formatAmount(row.clawback) }}</span>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.ind-actions {
  display: flex;
}
.ind-csv {
  margin: 0;
  overflow-x: auto;
  background: var(--line-soft);
  padding: var(--space-3);
  border-radius: var(--radius);
}
.ind-charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--space-4);
}
</style>
