<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import { AppButton, AppCard, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';
import BarChart from '../components/charts/BarChart.vue';
import DonutChart from '../components/charts/DonutChart.vue';
import HBarChart from '../components/charts/HBarChart.vue';
import LineChart from '../components/charts/LineChart.vue';

interface Amount {
  label: string;
  amount: number;
}
interface MonthNature {
  month: string;
  nature: string;
  amount: number;
}
interface MonthValue {
  month: string;
  primary: number;
  second: number | null;
  third: number | null;
}
interface Dashboard {
  entity: string;
  from: string | null;
  to: string | null;
  months: string[];
  byNature: Amount[];
  byGamme: Amount[];
  byMonthNature: MonthNature[];
  reprisesByMonth: MonthValue[];
  escompteByMonth: MonthValue[];
  reprises: number;
  escomptesVerses: number;
  escomptesApures: number;
  creance: number;
  debtorAccounts: number;
  topDistributors: Amount[];
  anomalies: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const from = ref('');
const to = ref('');
const product = ref('');
const distributor = ref('');
const group = ref('');
const delegation = ref('');
const nature = ref('');
const data = ref<Dashboard | null>(null);
const csv = ref('');
const error = ref('');
const loading = ref(false);

/**
 * Formats an amount, showing a dash for a missing value.
 * @param value the raw amount or null
 */
function amount(value: number | null): string {
  return value === null ? '—' : formatAmount(value);
}

/** Builds the query string from the current filter values. */
function query(): string {
  const params = new URLSearchParams();
  const add = (key: string, value: string) => {
    if (value) {
      params.set(key, value);
    }
  };
  add('from', from.value);
  add('to', to.value);
  add('product', product.value);
  add('distributor', distributor.value);
  add('group', group.value);
  add('delegation', delegation.value);
  add('nature', nature.value);
  const q = params.toString();
  return q ? `?${q}` : '';
}

/** Loads the dashboard aggregates for the entity over the filtered range. */
async function load(): Promise<void> {
  error.value = '';
  csv.value = '';
  loading.value = true;
  try {
    data.value = await apiGet<Dashboard>(`/api/ui/v1/reporting/${entity.value}/dashboard${query()}`);
  } catch (e) {
    data.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/** Fetches the CSV export of the dashboard lists for display. */
async function exportCsv(): Promise<void> {
  csv.value = await apiGetText(`/api/ui/v1/reporting/${entity.value}/export/dashboard${query()}`);
}

/** Caption shared by the charts, naming the entity and the unit. */
const caption = computed(() => (data.value ? `${entity.value} — en euros` : ''));

/** Stacked series of commissions per month and per nature. */
const stackedSeries = computed(() => {
  if (!data.value) {
    return [];
  }
  const natures = [...new Set(data.value.byMonthNature.map((m) => m.nature))];
  return natures.map((nature) => ({
    key: nature,
    label: nature,
    values: data.value!.months.map(
      (month) => data.value!.byMonthNature.find((m) => m.month === month && m.nature === nature)?.amount ?? 0,
    ),
  }));
});

/** Line series of escomptes versés, apurés and créance per month. */
const escompteSeries = computed(() => {
  if (!data.value) {
    return [];
  }
  return [
    { key: 'verses', label: 'Versés', values: data.value.escompteByMonth.map((e) => e.primary) },
    { key: 'apures', label: 'Apurés', values: data.value.escompteByMonth.map((e) => e.second ?? 0) },
    { key: 'creance', label: 'Créance', values: data.value.escompteByMonth.map((e) => e.third ?? 0) },
  ];
});

/** Line series of reprises per month. */
const reprisesSeries = computed(() => {
  if (!data.value) {
    return [];
  }
  return [{ key: 'reprises', label: 'Reprises', values: data.value.reprisesByMonth.map((r) => r.primary) }];
});

/** Donut data of commissions per nature. */
const natureData = computed(() => (data.value ? data.value.byNature.map((a) => ({ label: a.label, value: a.amount })) : []));
/** Donut data of commissions per gamme. */
const gammeData = computed(() => (data.value ? data.value.byGamme.map((a) => ({ label: a.label, value: a.amount })) : []));
/** Horizontal-bar data of the top distributors. */
const topData = computed(() => (data.value ? data.value.topDistributors.map((a) => ({ label: a.label, value: a.amount })) : []));

/** Nature rows of the detail table. */
const natureRows = computed(() => data.value?.byNature ?? []);
/** Gamme rows paired with their index, for the per-row test identifier. */
const gammeRows = computed(() => (data.value ? data.value.byGamme.map((g, i) => ({ ...g, i })) : []));
/** Escompte rows paired with their index, for the per-row test identifier. */
const escompteRows = computed(() => (data.value ? data.value.escompteByMonth.map((e, i) => ({ ...e, i })) : []));
/** Reprise rows paired with their index, for the per-row test identifier. */
const repriseRows = computed(() => (data.value ? data.value.reprisesByMonth.map((r, i) => ({ ...r, i })) : []));
/** Top-distributor rows paired with their index, for the per-row test identifier. */
const topRows = computed(() => (data.value ? data.value.topDistributors.map((d, i) => ({ ...d, i })) : []));

/** Columns of the commissions-by-nature table. */
const natureColumns: Column<Amount>[] = [
  { key: 'label', label: 'Nature' },
  { key: 'amount', label: 'Montant', align: 'num', value: (n) => amount(n.amount) },
];

/** Columns of the commissions-by-gamme table. */
const gammeColumns: Column<Amount & { i: number }>[] = [
  { key: 'label', label: 'Gamme' },
  { key: 'amount', label: 'Montant', align: 'num', value: (g) => amount(g.amount) },
];

/** Columns of the escomptes-by-month table. */
const escompteColumns: Column<MonthValue & { i: number }>[] = [
  { key: 'month', label: 'Mois' },
  { key: 'primary', label: 'Versés', align: 'num', value: (e) => amount(e.primary) },
  { key: 'second', label: 'Apurés', align: 'num', value: (e) => amount(e.second) },
  { key: 'third', label: 'Créance', align: 'num', value: (e) => amount(e.third) },
];

/** Columns of the reprises-by-month table. */
const repriseColumns: Column<MonthValue & { i: number }>[] = [
  { key: 'month', label: 'Mois' },
  { key: 'primary', label: 'Montant', align: 'num', value: (r) => amount(r.primary) },
];

/** Columns of the top-distributors table. */
const topColumns: Column<Amount & { i: number }>[] = [
  { key: 'label', label: 'Distributeur' },
  { key: 'amount', label: 'Montant', align: 'num', value: (d) => amount(d.amount) },
];

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading title="Tableaux de bord" subtitle="Commissions par mois, nature et gamme ; reprises ; escomptes versés, apurés et créance ; comptes débiteurs ; premiers distributeurs ; anomalies — sur une plage de mois (REP-01, REP-03)." />

    <FilterBar
      :loading="loading"
      search-label="Rafraîchir"
      search-testid="dash-refresh"
      :resettable="false"
      @search="load"
    >
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="dash-entity" type="text" v-model="entity" />
      </FormField>
      <FormField label="Du mois" stacked width="date" v-slot="{ id }">
        <input :id="id" data-testid="dash-from" type="text" v-model="from" placeholder="2026-06-01" />
      </FormField>
      <FormField label="Au mois" stacked width="date" v-slot="{ id }">
        <input :id="id" data-testid="dash-to" type="text" v-model="to" placeholder="2026-11-01" />
      </FormField>
      <FormField label="Produit" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="dash-product" type="text" v-model="product" />
      </FormField>
      <FormField label="Distributeur" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="dash-distributor" type="text" v-model="distributor" />
      </FormField>
      <FormField label="Groupement" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="dash-group" type="text" v-model="group" />
      </FormField>
      <FormField label="Délégation" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="dash-delegation" type="text" v-model="delegation" />
      </FormField>
      <FormField label="Nature" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="dash-nature-filter" type="text" v-model="nature" />
      </FormField>
    </FilterBar>

    <div class="dash-actions">
      <AppButton variant="secondary" data-testid="dash-export" @click="exportCsv">Exporter CSV</AppButton>
    </div>

    <AppMessage v-if="error" variant="error" data-testid="dash-error">{{ error }}</AppMessage>

    <AppCard v-if="csv">
      <pre data-testid="dash-export-content" class="dash-csv">{{ csv }}</pre>
    </AppCard>

    <template v-if="data">
      <div class="dash-stats">
        <div class="card dash-stat">
          <span class="dash-stat__label">Reprises</span>
          <span class="dash-stat__value" data-testid="dash-reprises">{{ amount(data.reprises) }}</span>
        </div>
        <div class="card dash-stat">
          <span class="dash-stat__label">Escomptes versés</span>
          <span class="dash-stat__value" data-testid="dash-verses">{{ amount(data.escomptesVerses) }}</span>
        </div>
        <div class="card dash-stat">
          <span class="dash-stat__label">Escomptes apurés</span>
          <span class="dash-stat__value" data-testid="dash-apures">{{ amount(data.escomptesApures) }}</span>
        </div>
        <div class="card dash-stat">
          <span class="dash-stat__label">Créance d'escompte</span>
          <span class="dash-stat__value" data-testid="dash-creance">{{ amount(data.creance) }}</span>
        </div>
        <div class="card dash-stat">
          <span class="dash-stat__label">Comptes débiteurs</span>
          <span class="dash-stat__value" data-testid="dash-debtors">{{ data.debtorAccounts }}</span>
        </div>
        <div class="card dash-stat">
          <span class="dash-stat__label">Anomalies</span>
          <span class="dash-stat__value" data-testid="dash-anomalies">{{ data.anomalies }}</span>
        </div>
      </div>

      <AppCard>
        <BarChart
          mode="stacked"
          :categories="data.months"
          :series="stackedSeries"
          title="Commissions par mois et par nature"
          :caption="caption"
          testid="chart-month-nature"
        />
      </AppCard>

      <AppCard>
        <DonutChart :data="natureData" title="Répartition par nature" :caption="caption" testid="chart-nature" />
        <h2 class="dash-block-title">Commissions par nature</h2>
        <DataTable
          :columns="natureColumns"
          :rows="natureRows"
          empty="Aucune commission."
          empty-testid="no-nature"
          :row-testid="(n) => `dash-nature-${n.label}`"
        />
      </AppCard>

      <AppCard>
        <DonutChart :data="gammeData" title="Répartition par gamme" :caption="caption" testid="chart-gamme" />
        <h2 class="dash-block-title">Commissions par gamme</h2>
        <DataTable
          :columns="gammeColumns"
          :rows="gammeRows"
          empty="Aucune commission."
          :row-testid="(g) => `dash-gamme-${g.i}`"
        />
      </AppCard>

      <AppCard>
        <LineChart
          :categories="data.months"
          :series="escompteSeries"
          title="Escomptes versés, apurés et créance dans le temps"
          :caption="caption"
          testid="chart-escompte"
        />
        <h2 class="dash-block-title">Escomptes par mois</h2>
        <DataTable
          :columns="escompteColumns"
          :rows="escompteRows"
          empty="Aucun escompte."
          :row-testid="(e) => `dash-escompte-${e.i}`"
        />
      </AppCard>

      <AppCard>
        <LineChart
          :categories="data.months"
          :series="reprisesSeries"
          title="Reprises par mois"
          :caption="caption"
          testid="chart-reprises"
        />
        <h2 class="dash-block-title">Reprises par mois</h2>
        <DataTable
          :columns="repriseColumns"
          :rows="repriseRows"
          empty="Aucune reprise."
          :row-testid="(r) => `dash-reprise-${r.i}`"
        />
      </AppCard>

      <AppCard>
        <HBarChart :data="topData" title="Premiers distributeurs" :caption="caption" testid="chart-top" />
        <h2 class="dash-block-title">Premiers distributeurs</h2>
        <DataTable
          :columns="topColumns"
          :rows="topRows"
          empty="Aucun distributeur."
          :row-testid="(d) => `dash-top-${d.i}`"
        />
      </AppCard>
    </template>
  </div>
</template>

<style scoped>
.dash-actions {
  display: flex;
}
.dash-csv {
  margin: 0;
  overflow-x: auto;
  background: var(--line-soft);
  padding: var(--space-3);
  border-radius: var(--radius);
}
.dash-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: var(--space-4);
}
.dash-stat {
  padding: var(--space-4) var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.dash-stat__label {
  font-size: var(--fs-body);
  color: var(--muted);
}
.dash-stat__value {
  font-family: ui-monospace, monospace;
  font-size: var(--fs-title);
  font-weight: 500;
}
.dash-block-title {
  margin: 0;
  font-size: var(--fs-h2);
  font-weight: 600;
}
</style>
