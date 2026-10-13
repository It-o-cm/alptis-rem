<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { cutoffViewLabel } from '../presentation/reporting';
import { formatAmount } from '../presentation/format';
import BarChart from '../components/charts/BarChart.vue';
import { AppButton, AppCard, AppMessage, DataTable, EntitySelect, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface Row {
  label: string;
  forecast: number;
  earned: number;
  adjustments: number;
  paid: number;
  balance: number;
  remaining: number;
}

interface Report {
  view: string;
  rows: Row[];
  total: Row;
}

type DisplayRow = Row & { i: number; total: boolean };

const VIEWS = ['COVERAGE', 'DISTRIBUTOR', 'PRODUCT', 'ENTITY'];

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const date = ref('2026-09-30');
const view = ref('ENTITY');
const report = ref<Report | null>(null);
const error = ref('');
const loading = ref(false);

/**
 * French label of a reading axis.
 * @param v the axis code
 */
function viewLabel(v: string): string {
  return cutoffViewLabel(v);
}

/**
 * Formats an amount with the common currency formatting.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Loads the cut-off report for the current entity, date and reading axis. */
async function load() {
  error.value = '';
  loading.value = true;
  try {
    report.value = await apiGet<Report>(
      `/api/ui/v1/accounting/${entity.value}/cutoff?date=${date.value}&view=${view.value}`,
    );
  } catch (e) {
    report.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/**
 * Switches the reading axis and reloads the report.
 * @param v the chosen axis code
 */
function pick(v: string) {
  view.value = v;
  load();
}

/** Columns of the cut-off table, the first header being the active axis. */
const columns = computed<Column<DisplayRow>[]>(() => [
  { key: 'label', label: report.value ? viewLabel(report.value.view) : '' },
  { key: 'forecast', label: 'Prévisionnel', align: 'num' },
  { key: 'earned', label: 'Acquis cumulé', align: 'num' },
  { key: 'adjustments', label: 'Régularisations cumulées', align: 'num', value: (r) => amount(r.adjustments) },
  { key: 'paid', label: 'Payé cumulé', align: 'num', value: (r) => amount(r.paid) },
  { key: 'balance', label: 'Solde à payer', align: 'num' },
  { key: 'remaining', label: 'Reste à acquérir', align: 'num', value: (r) => amount(r.remaining) },
]);

/** Report rows paired with their index, followed by the displayed total. */
const cutoffRows = computed<DisplayRow[]>(() => {
  if (!report.value) {
    return [];
  }
  const rows = report.value.rows.map((r, i) => ({ ...r, i, total: false }));
  if (rows.length === 0) {
    return [];
  }
  return [...rows, { ...report.value.total, i: -1, total: true, label: 'Total affiché' }];
});

const cutoffCategories = computed(() => (report.value ? report.value.rows.map((r) => r.label) : []));
const cutoffSeries = computed(() => {
  if (!report.value) {
    return [];
  }
  const rows = report.value.rows;
  return [
    { key: 'earned', label: 'Acquis', values: rows.map((r) => r.earned) },
    { key: 'paid', label: 'Payé', values: rows.map((r) => r.paid) },
    { key: 'balance', label: 'Solde à payer', values: rows.map((r) => r.balance) },
    { key: 'remaining', label: 'Reste à acquérir', values: rows.map((r) => r.remaining) },
  ];
});

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="État de cut-off"
      subtitle="Ce qui était prévu, ce qui est acquis, ce qui a été payé et ce qui reste, à une date d'arrêté. Les deux dernières colonnes sont calculées (section 10.4)."
    />

    <FilterBar :loading="loading" search-label="Rafraîchir" search-testid="cutoff-refresh" :resettable="false" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="cutoff-entity" v-model="entity" />
      </FormField>
      <FormField label="Date d'arrêté" stacked width="date" v-slot="{ id }">
        <input :id="id" data-testid="cutoff-date" type="text" v-model="date" />
      </FormField>
    </FilterBar>

    <div class="cutoff-views">
      <span class="cutoff-views__label">Lire par :</span>
      <AppButton
        v-for="v in VIEWS"
        :key="v"
        :variant="view === v ? 'primary' : 'secondary'"
        :data-testid="`cutoff-view-${v}`"
        @click="pick(v)"
      >
        {{ viewLabel(v) }}
      </AppButton>
    </div>

    <AppMessage v-if="error" variant="error" data-testid="cutoff-error">{{ error }}</AppMessage>

    <AppCard v-if="report && report.rows.length > 0" flush>
      <BarChart
        mode="stacked"
        :categories="cutoffCategories"
        :series="cutoffSeries"
        title="Acquis, payé, solde à payer et reste à acquérir"
        caption="en euros"
        testid="chart-cutoff"
      />
    </AppCard>

    <AppCard v-if="report" flush>
      <DataTable
        :columns="columns"
        :rows="cutoffRows"
        empty="Aucune ligne."
        empty-testid="no-cutoff-row"
        :row-testid="(r) => (r.total ? 'cutoff-total-row' : `cutoff-row-${r.i}`)"
      >
        <template #cell-forecast="{ row }">
          <span v-if="row.total" data-testid="cutoff-total-forecast">{{ amount(row.forecast) }}</span>
          <template v-else>{{ amount(row.forecast) }}</template>
        </template>
        <template #cell-earned="{ row }">
          <span v-if="row.total" data-testid="cutoff-total-earned">{{ amount(row.earned) }}</span>
          <template v-else>{{ amount(row.earned) }}</template>
        </template>
        <template #cell-balance="{ row }">
          <span v-if="row.total">{{ amount(row.balance) }}</span>
          <span v-else :data-testid="`cutoff-balance-${row.i}`">{{ amount(row.balance) }}</span>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.cutoff-views {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}
.cutoff-views__label {
  font-size: var(--fs-body);
  color: var(--muted);
}
</style>
