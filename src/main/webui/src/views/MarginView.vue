<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiGetText, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import { AppButton, AppCard, AppMessage, DataTable, EntitySelect, FilterBar, FormField, MonthSelect, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';
import HBarChart from '../components/charts/HBarChart.vue';

interface MarginRow {
  key: string;
  received: number;
  paid: number;
  margin: number;
}
interface Margin {
  byProduct: MarginRow[];
  byInsurer: MarginRow[];
  byDistributor: MarginRow[];
}

interface InsurerReconRow {
  insurer: string;
  expected: number;
  received: number;
  gap: number;
}
interface InsurerReconciliation {
  insurers: InsurerReconRow[];
}

/**
 * Maps margin rows to the horizontal-bar chart data.
 * @param rows the margin rows of an axis
 */
function marginData(rows: MarginRow[]): { label: string; value: number }[] {
  return rows.map((r) => ({ label: r.key, value: r.margin }));
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const data = ref<Margin | null>(null);
const insurerRecon = ref<InsurerReconRow[]>([]);
const csv = ref('');
const error = ref('');
const message = ref('');
const loading = ref(false);
const month = ref('2026-09-01');

/** Whether the current user may record the insurer settlement (Finance). */
const canSettle = (): boolean => session.roles.includes('FINANCE_CONTROLLER');

/** Records the insurer's payment advice for the month, settling its received commissions (FIN-08). */
async function settleInsurer(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ settled: number }>(
      `/api/ui/v1/reporting/${entity.value}/${month.value}/insurer-settlement`, {});
    message.value = `Règlement assureur enregistré : ${result.settled} ligne(s) soldée(s).`;
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Règlement refusé';
  }
}

/**
 * Formats an amount with the common currency formatting.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Loads the margin aggregates of the entity by product, insurer and distributor. */
async function load(): Promise<void> {
  error.value = '';
  csv.value = '';
  loading.value = true;
  try {
    data.value = await apiGet<Margin>(`/api/ui/v1/reporting/${entity.value}/margin`);
    const recon = await apiGet<InsurerReconciliation>(
      `/api/ui/v1/reporting/${entity.value}/insurer-reconciliation`);
    insurerRecon.value = recon.insurers;
  } catch (e) {
    data.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/** Fetches the CSV export of the margin lists for display. */
async function exportCsv(): Promise<void> {
  csv.value = await apiGetText(`/api/ui/v1/reporting/${entity.value}/export/margin`);
}

/** The three margin axes, each with its rows paired with their index. */
const axes = computed(() =>
  data.value
    ? [
        { id: 'product', label: 'Par produit', rows: data.value.byProduct.map((r, i) => ({ ...r, i })) },
        { id: 'insurer', label: 'Par assureur', rows: data.value.byInsurer.map((r, i) => ({ ...r, i })) },
        { id: 'distributor', label: 'Par distributeur', rows: data.value.byDistributor.map((r, i) => ({ ...r, i })) },
      ]
    : [],
);

/** Columns of each margin axis table. */
const columns: Column<MarginRow & { i: number }>[] = [
  { key: 'key', label: 'Clé' },
  { key: 'received', label: 'Reçu', align: 'num', value: (r) => amount(r.received) },
  { key: 'paid', label: 'Payé', align: 'num', value: (r) => amount(r.paid) },
  { key: 'margin', label: 'Marge', align: 'num', value: (r) => amount(r.margin) },
];

/** Columns of the insurer reconciliation table (FIN-08). */
const reconColumns: Column<InsurerReconRow>[] = [
  { key: 'insurer', label: 'Assureur' },
  { key: 'expected', label: 'Attendu', align: 'num', value: (r) => amount(r.expected) },
  { key: 'received', label: 'Reçu', align: 'num', value: (r) => amount(r.received) },
  { key: 'gap', label: 'Écart', align: 'num', value: (r) => amount(r.gap) },
];

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading title="Marge" subtitle="Marge (commission reçue − commission payée) par produit, par assureur et par distributeur (REP-08)." />

    <FilterBar
      :loading="loading"
      search-label="Rafraîchir"
      search-testid="margin-refresh"
      :resettable="false"
      @search="load"
    >
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="margin-entity" v-model="entity" />
      </FormField>
      <FormField label="Mois (règlement assureur)" stacked width="short" v-slot="{ id }">
        <MonthSelect :id="id" data-testid="margin-month" v-model="month" :entity="entity" />
      </FormField>
    </FilterBar>

    <div class="margin-actions">
      <AppButton variant="secondary" data-testid="margin-export" @click="exportCsv">Exporter CSV</AppButton>
      <AppButton v-if="canSettle()" variant="secondary" data-testid="margin-settle" @click="settleInsurer">
        Enregistrer le règlement assureur
      </AppButton>
    </div>

    <AppMessage v-if="error" variant="error" data-testid="margin-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="margin-message">{{ message }}</AppMessage>

    <AppCard v-if="csv">
      <pre data-testid="margin-export-content" class="margin-csv">{{ csv }}</pre>
    </AppCard>

    <AppCard v-if="data" data-testid="insurer-recon" title="Rapprochement assureurs : attendu et reçu (FIN-08)">
      <DataTable
        :columns="reconColumns"
        :rows="insurerRecon"
        empty="Aucune commission d'assureur."
        empty-testid="recon-empty"
        :row-testid="(r) => `recon-row-${r.insurer}`"
      />
    </AppCard>

    <template v-if="data">
      <AppCard v-for="axis in axes" :key="axis.id">
        <HBarChart
          :data="marginData(axis.rows)"
          :title="`Marge ${axis.label.toLowerCase()}`"
          caption="en euros"
          :testid="`chart-margin-${axis.id}`"
        />
        <h2 class="margin-block-title">{{ axis.label }}</h2>
        <DataTable
          :columns="columns"
          :rows="axis.rows"
          empty="Aucune donnée."
          :empty-testid="`margin-empty-${axis.id}`"
          :row-testid="(r) => `margin-${axis.id}-${r.i}`"
        />
      </AppCard>
    </template>
  </div>
</template>

<style scoped>
.margin-actions {
  display: flex;
}
.margin-csv {
  margin: 0;
  overflow-x: auto;
  background: var(--line-soft);
  padding: var(--space-3);
  border-radius: var(--radius);
}
.margin-block-title {
  margin: 0;
  font-size: var(--fs-h2);
  font-weight: 600;
}
</style>
