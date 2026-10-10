<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import { AppButton, AppCard, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface SearchRow {
  distributor: string;
  product: string;
  gamme: string;
  nature: string;
  basis: number | null;
  rate: number | null;
  amount: number;
  rule: string;
  statementNumber: string;
  month: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const filters = ref({ product: '', distributor: '', group: '', delegation: '', nature: '', month: '' });
const rows = ref<SearchRow[]>([]);
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
  add('product', filters.value.product);
  add('distributor', filters.value.distributor);
  add('group', filters.value.group);
  add('delegation', filters.value.delegation);
  add('nature', filters.value.nature);
  add('month', filters.value.month);
  const q = params.toString();
  return q ? `?${q}` : '';
}

/** Searches the commission lines of the entity matching the filters. */
async function search(): Promise<void> {
  error.value = '';
  csv.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<SearchRow[]>(`/api/ui/v1/reporting/${entity.value}/search${query()}`);
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Recherche impossible';
  } finally {
    loading.value = false;
  }
}

/** Fetches the CSV export of the search results for display. */
async function exportCsv(): Promise<void> {
  csv.value = await apiGetText(`/api/ui/v1/reporting/${entity.value}/export/search${query()}`);
}

/** Search rows paired with their index, for the per-row test identifier. */
const indexedRows = computed(() => rows.value.map((r, i) => ({ ...r, i })));

/** Columns of the commission-lines table. */
const columns: Column<SearchRow & { i: number }>[] = [
  { key: 'distributor', label: 'Distributeur' },
  { key: 'product', label: 'Produit' },
  { key: 'gamme', label: 'Gamme' },
  { key: 'nature', label: 'Nature' },
  { key: 'basis', label: 'Assiette', align: 'num', value: (r) => amount(r.basis) },
  { key: 'rate', label: 'Taux', align: 'num', value: (r) => r.rate ?? '—' },
  { key: 'amount', label: 'Montant', align: 'num', value: (r) => amount(r.amount) },
  { key: 'rule', label: 'Règle' },
  { key: 'statementNumber', label: 'Bordereau' },
  { key: 'month', label: 'Mois', value: (r) => r.month ?? '—' },
];

onMounted(search);
</script>

<template>
  <div class="page">
    <PageHeading title="Recherche de lignes de commission" subtitle="Combien a été payé, à qui, quand et pourquoi : la ligne, sa gamme, son assiette, son taux, sa règle et son bordereau (REP-02, REP-03, FIN-07)." />

    <FilterBar :loading="loading" search-testid="cs-search" :resettable="false" @search="search">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="cs-entity" type="text" v-model="entity" />
      </FormField>
      <FormField label="Produit" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="cs-product" type="text" v-model="filters.product" />
      </FormField>
      <FormField label="Distributeur" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="cs-distributor" type="text" v-model="filters.distributor" />
      </FormField>
      <FormField label="Groupement" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="cs-group" type="text" v-model="filters.group" />
      </FormField>
      <FormField label="Délégation" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="cs-delegation" type="text" v-model="filters.delegation" />
      </FormField>
      <FormField label="Nature" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="cs-nature-filter" type="text" v-model="filters.nature" />
      </FormField>
      <FormField label="Mois" stacked width="date" v-slot="{ id }">
        <input :id="id" data-testid="cs-month" type="text" v-model="filters.month" />
      </FormField>
    </FilterBar>

    <div class="cs-actions">
      <AppButton variant="secondary" data-testid="cs-export" @click="exportCsv">Exporter CSV</AppButton>
    </div>

    <AppMessage v-if="error" variant="error" data-testid="cs-error">{{ error }}</AppMessage>

    <AppCard v-if="csv">
      <pre data-testid="cs-export-content" class="cs-csv">{{ csv }}</pre>
    </AppCard>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="indexedRows"
        :loading="loading"
        empty="Aucune ligne."
        empty-testid="no-row"
        :row-testid="(r) => `cs-row-${r.i}`"
      >
        <template #cell-nature="{ row }">
          <span :data-testid="`cs-nature-${row.i}`">{{ row.nature }}</span>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.cs-actions {
  display: flex;
}
.cs-csv {
  margin: 0;
  overflow-x: auto;
  background: var(--line-soft);
  padding: var(--space-3);
  border-radius: var(--radius);
}
</style>
