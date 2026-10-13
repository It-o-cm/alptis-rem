<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount, formatDate } from '../presentation/format';
import { monthLabel } from '../presentation/months';
import { lineNatureLabel, lineStatusLabel, directionLabel, NATURE_OPTIONS } from '../presentation/lines';
import { eventTypeLabel } from '../presentation/events';
import {
  AppButton,
  AppCard,
  AppMessage,
  CodeSelect,
  DataTable,
  DelegationSelect,
  DetailList,
  DetailRow,
  DistributorSelect,
  EntitySelect,
  FilterBar,
  FormField,
  GroupSelect,
  MonthSelect,
  PageHeading,
  ProductSelect,
} from '../components/ui';
import type { Column } from '../components/ui';

interface SearchRow {
  id: number;
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

interface LineDetail {
  id: number;
  entityCode: string;
  nature: string;
  direction: string | null;
  status: string | null;
  contractNumber: string | null;
  coverageCode: string | null;
  eventType: string | null;
  eventDate: string | null;
  ruleVersion: string | null;
  basis: number | null;
  rate: number | null;
  amount: number;
  attachmentMonth: string | null;
  premiumFiscalYear: number | null;
  reason: string | null;
  distributor: string;
  statementNumber: string | null;
  paymentOrder: string | null;
  explanation: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const filters = ref({ product: '', distributor: '', group: '', delegation: '', nature: '', month: '' });
const rows = ref<SearchRow[]>([]);
const detail = ref<LineDetail | null>(null);
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

/**
 * Opens a line's detail with its native explanation (CAL-10).
 * @param row the selected search row
 */
async function openDetail(row: SearchRow): Promise<void> {
  detail.value = await apiGet<LineDetail>(`/api/ui/v1/commission-lines/${row.id}`);
}

/** The stored explanation parsed into label/value entries for display in clear. */
const explanationEntries = computed<{ key: string; value: string }[]>(() => {
  const raw = detail.value?.explanation;
  if (!raw) {
    return [];
  }
  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return [{ key: 'explication', value: raw }];
  }
  return Object.entries(parsed).map(([key, value]) => ({
    key,
    value: typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value),
  }));
});

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
  { key: 'month', label: 'Mois', value: (r) => monthLabel(r.month) },
];

onMounted(search);
</script>

<template>
  <div class="page">
    <PageHeading title="Recherche de lignes de commission" subtitle="Combien a été payé, à qui, quand et pourquoi : la ligne, sa gamme, son assiette, son taux, sa règle et son bordereau (REP-02, REP-03, FIN-07)." />

    <FilterBar :loading="loading" search-testid="cs-search" :resettable="false" @search="search">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="cs-entity" v-model="entity" />
      </FormField>
      <FormField label="Produit" stacked width="short" v-slot="{ id }">
        <ProductSelect :id="id" data-testid="cs-product" v-model="filters.product" allow-empty />
      </FormField>
      <FormField label="Distributeur" stacked width="short" v-slot="{ id }">
        <DistributorSelect :id="id" data-testid="cs-distributor" v-model="filters.distributor" allow-empty />
      </FormField>
      <FormField label="Groupement" stacked width="short" v-slot="{ id }">
        <GroupSelect :id="id" data-testid="cs-group" v-model="filters.group" allow-empty />
      </FormField>
      <FormField label="Délégation" stacked width="short" v-slot="{ id }">
        <DelegationSelect :id="id" data-testid="cs-delegation" v-model="filters.delegation" allow-empty />
      </FormField>
      <FormField label="Nature" stacked width="short" v-slot="{ id }">
        <CodeSelect :id="id" data-testid="cs-nature-filter" v-model="filters.nature" :options="NATURE_OPTIONS" allow-empty />
      </FormField>
      <FormField label="Mois" stacked width="date" v-slot="{ id }">
        <MonthSelect :id="id" data-testid="cs-month" v-model="filters.month" allow-empty />
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
        clickable
        :row-testid="(r) => `cs-row-${r.i}`"
        @row-click="openDetail"
      >
        <template #cell-nature="{ row }">
          <span :data-testid="`cs-nature-${row.i}`">{{ lineNatureLabel(row.nature) }}</span>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="line-detail">
      <h2 class="cs-detail-title">Ligne {{ lineNatureLabel(detail.nature) }} — {{ amount(detail.amount) }}</h2>
      <DetailList>
        <DetailRow label="Statut">{{ lineStatusLabel(detail.status) }}</DetailRow>
        <DetailRow label="Sens">{{ directionLabel(detail.direction) }}</DetailRow>
        <DetailRow label="Distributeur">{{ detail.distributor }}</DetailRow>
        <DetailRow label="Contrat">{{ detail.contractNumber ?? '—' }}</DetailRow>
        <DetailRow label="Garantie">{{ detail.coverageCode ?? '—' }}</DetailRow>
        <DetailRow label="Événement">
          {{ detail.eventType ? `${eventTypeLabel(detail.eventType)}${detail.eventDate ? ' · ' + formatDate(detail.eventDate) : ''}` : '—' }}
        </DetailRow>
        <DetailRow label="Version de règle">{{ detail.ruleVersion ?? '—' }}</DetailRow>
        <DetailRow label="Assiette">{{ amount(detail.basis) }}</DetailRow>
        <DetailRow label="Taux">{{ detail.rate ?? '—' }}</DetailRow>
        <DetailRow label="Mois de rattachement">{{ monthLabel(detail.attachmentMonth) }}</DetailRow>
        <DetailRow label="Exercice de prime">{{ detail.premiumFiscalYear ?? '—' }}</DetailRow>
        <DetailRow label="Bordereau">{{ detail.statementNumber ?? '—' }}</DetailRow>
        <DetailRow label="Ordre de paiement">{{ detail.paymentOrder ?? '—' }}</DetailRow>
        <DetailRow label="Motif">{{ detail.reason ?? '—' }}</DetailRow>
      </DetailList>

      <h3 class="cs-detail-sub">Explication du calcul</h3>
      <AppMessage v-if="explanationEntries.length === 0" variant="info" data-testid="no-explanation">
        Aucune explication enregistrée.
      </AppMessage>
      <DetailList v-else data-testid="line-explanation">
        <DetailRow v-for="entry in explanationEntries" :key="entry.key" :label="entry.key">
          {{ entry.value }}
        </DetailRow>
      </DetailList>
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
.cs-detail-title {
  margin: 0;
  font-size: var(--fs-h2);
}
.cs-detail-sub {
  margin: 0;
  font-size: var(--fs-h3);
  font-weight: 600;
}
</style>
