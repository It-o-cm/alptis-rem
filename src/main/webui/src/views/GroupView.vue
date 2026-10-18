<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { formatAmount } from '../presentation/format';
import {
  AppButton, AppCard, AppMessage, DataTable, DistributorSelect, FormField, MonthSelect, PageHeading,
} from '../components/ui';
import type { Column } from '../components/ui';
import BarChart from '../components/charts/BarChart.vue';

interface EntitySummary {
  code: string;
  name: string;
  total: number;
}
interface EntityMonth {
  entity: string;
  month: string;
  total: number;
}
interface Consolidated {
  months: string[];
  summaries: EntitySummary[];
  cells: EntityMonth[];
}

interface ConsolidatedRow {
  section: string;
  label: string;
  amount: number;
}
interface ConsolidatedStatement {
  beneficiary: string;
  rows: ConsolidatedRow[];
  net: number;
}

const data = ref<Consolidated | null>(null);
const csv = ref('');
const error = ref('');
const forbidden = ref(false);
const consoPartner = ref('');
const consoMonth = ref('');
const consolidated = ref<ConsolidatedStatement | null>(null);

/** Columns of the consolidated bordereau: one subtotal row per entity. */
const consoColumns: Column<ConsolidatedRow>[] = [
  { key: 'label', label: 'Entité' },
  { key: 'amount', label: 'Sous-total', align: 'num', value: (r) => amount(r.amount) },
];

/** Loads the consolidated multi-entity bordereau of a distributor for a month (BRD-04). */
async function loadConsolidated(): Promise<void> {
  error.value = '';
  try {
    consolidated.value = await apiGet<ConsolidatedStatement>(
      `/api/ui/v1/documents/consolidated/${consoPartner.value}/${consoMonth.value}`,
    );
  } catch (e) {
    consolidated.value = null;
    error.value = e instanceof ApiError ? e.message : 'Bordereau consolidé introuvable';
  }
}

/**
 * Formats an amount with the common currency formatting.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Loads the consolidated group view, flagging the forbidden case. */
async function load(): Promise<void> {
  error.value = '';
  forbidden.value = false;
  csv.value = '';
  try {
    data.value = await apiGet<Consolidated>('/api/ui/v1/reporting/consolidated');
  } catch (e) {
    data.value = null;
    if (e instanceof ApiError && e.status === 404) {
      forbidden.value = true;
    } else {
      error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
    }
  }
}

/** Fetches the CSV export of the consolidated view for display. */
async function exportCsv(): Promise<void> {
  csv.value = await apiGetText('/api/ui/v1/reporting/export/consolidated');
}

/**
 * Commission total of an entity for a month, or null when absent.
 * @param entity the entity code
 * @param month the month
 */
function cell(entity: string, month: string): number | null {
  const found = data.value?.cells.find((c) => c.entity === entity && c.month === month);
  return found ? found.total : null;
}

/** Grouped bar series of each entity's commissions per month. */
const entitySeries = computed(() => {
  if (!data.value) {
    return [];
  }
  return data.value.summaries.map((e) => ({
    key: e.code,
    label: e.code,
    values: data.value!.months.map((m) => cell(e.code, m) ?? 0),
  }));
});

/** Columns of the total-per-entity table. */
const summaryColumns: Column<EntitySummary>[] = [
  { key: 'name', label: 'Entité' },
  { key: 'total', label: 'Commissions', align: 'num', value: (e) => amount(e.total) },
];

/** Columns of the entities-by-month table, one numeric column per month. */
const monthColumns = computed<Column<EntitySummary>[]>(() => {
  const cols: Column<EntitySummary>[] = [{ key: 'code', label: 'Entité' }];
  for (const m of data.value?.months ?? []) {
    cols.push({ key: m, label: m, align: 'num', value: (e) => amount(cell(e.code, m) ?? 0) });
  }
  return cols;
});

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading title="Vision consolidée groupe" subtitle="Commissions de chaque entité, mois par mois, pour les utilisateurs groupe (REP-07)." />

    <AppCard v-if="forbidden" data-testid="group-forbidden">
      La vision consolidée est réservée aux utilisateurs de niveau groupe.
    </AppCard>

    <template v-else>
      <div class="group-actions">
        <AppButton variant="secondary" data-testid="group-export" @click="exportCsv">Exporter CSV</AppButton>
      </div>

      <AppMessage v-if="error" variant="error" data-testid="group-error">{{ error }}</AppMessage>

      <AppCard v-if="csv">
        <pre data-testid="group-export-content" class="group-csv">{{ csv }}</pre>
      </AppCard>

      <AppCard v-if="data">
        <h2 class="group-block-title">Total par entité</h2>
        <DataTable
          :columns="summaryColumns"
          :rows="data.summaries"
          row-key="code"
          empty="Aucune entité."
          :row-testid="(e) => `group-row-${e.code}`"
        />
      </AppCard>

      <AppCard v-if="data">
        <BarChart
          mode="grouped"
          :categories="data.months"
          :series="entitySeries"
          title="Commissions des entités, mois par mois"
          caption="en euros"
          testid="chart-group"
        />
      </AppCard>

      <AppCard v-if="data">
        <h2 class="group-block-title">Entités par mois</h2>
        <DataTable
          :columns="monthColumns"
          :rows="data.summaries"
          row-key="code"
          empty="Aucune entité."
          :row-testid="(e) => `group-month-row-${e.code}`"
        >
          <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
        </DataTable>
      </AppCard>

      <AppCard title="Bordereau consolidé multi-entités (BRD-04)" data-testid="conso-form">
        <div class="ui-bar">
          <FormField label="Distributeur" stacked width="short" v-slot="{ id }">
            <DistributorSelect :id="id" data-testid="conso-partner" v-model="consoPartner" allow-empty />
          </FormField>
          <FormField label="Mois" stacked width="short" v-slot="{ id }">
            <MonthSelect :id="id" data-testid="conso-month" v-model="consoMonth" allow-empty />
          </FormField>
          <AppButton variant="secondary" data-testid="conso-btn" @click="loadConsolidated">
            Bordereau consolidé
          </AppButton>
        </div>
      </AppCard>

      <AppCard v-if="consolidated" data-testid="conso-detail">
        <h2 class="group-block-title">{{ consolidated.beneficiary }}</h2>
        <DataTable
          :columns="consoColumns"
          :rows="consolidated.rows"
          :row-key="(r) => consolidated?.rows.indexOf(r) ?? 0"
          empty="Aucune entité."
          :row-testid="(r) => `conso-row-${consolidated?.rows.indexOf(r)}`"
        />
        <p class="group-block-title">
          Total : <span data-testid="conso-net">{{ amount(consolidated.net) }}</span>
        </p>
      </AppCard>
    </template>
  </div>
</template>

<style scoped>
.group-actions {
  display: flex;
}
.group-csv {
  margin: 0;
  overflow-x: auto;
  background: var(--line-soft);
  padding: var(--space-3);
  border-radius: var(--radius);
}
.group-block-title {
  margin: 0;
  font-size: var(--fs-h2);
  font-weight: 600;
}
</style>
