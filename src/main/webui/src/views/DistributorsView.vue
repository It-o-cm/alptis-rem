<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppCard, AppMessage, DataTable, DetailList, DetailRow, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface DistributorView {
  partnerCode: string;
  legalName: string;
  distributorType: string | null;
  groupCode: string | null;
}

interface StatusView {
  entityCode: string;
  status: string;
  validFrom: string | null;
  validTo: string | null;
}

interface ProductionCodeView {
  entityCode: string;
  code: string;
  label: string | null;
  status: string | null;
}

interface DistributorDetail {
  partnerCode: string;
  legalName: string;
  groupCode: string | null;
  statuses: StatusView[];
  productionCodes: ProductionCodeView[];
}

const term = ref('');
const results = ref<DistributorView[]>([]);
const detail = ref<DistributorDetail | null>(null);
const error = ref('');
const loading = ref(false);

/** Columns of the distributor results table. */
const columns: Column<DistributorView>[] = [
  { key: 'partnerCode', label: 'Code partenaire' },
  { key: 'legalName', label: 'Raison sociale' },
  { key: 'distributorType', label: 'Type', value: (d) => d.distributorType ?? '—' },
  { key: 'groupCode', label: 'Groupement', value: (d) => d.groupCode ?? '—' },
];

/** Columns of the per-entity status table. */
const statusColumns: Column<StatusView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'status', label: 'Statut' },
  { key: 'validFrom', label: 'Depuis', value: (s) => s.validFrom ?? '—' },
  { key: 'validTo', label: "Jusqu'à", value: (s) => s.validTo ?? '—' },
];

/** Columns of the production-codes table. */
const codeColumns: Column<ProductionCodeView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé', value: (c) => c.label ?? '—' },
  { key: 'status', label: 'Statut', value: (c) => c.status ?? '—' },
];

/** Searches distributors by code or legal name within the user's entities. */
async function search(): Promise<void> {
  error.value = '';
  detail.value = null;
  loading.value = true;
  try {
    const page = await apiGet<{ items: DistributorView[] }>(
      `/api/ui/v1/distributors?q=${encodeURIComponent(term.value)}`,
    );
    results.value = page.items;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de recherche';
  } finally {
    loading.value = false;
  }
}

/** Clears the search term and its results. */
function reset(): void {
  term.value = '';
  results.value = [];
  detail.value = null;
  error.value = '';
}

/**
 * Loads the full detail of a distributor.
 * @param distributor the selected row
 */
async function open(distributor: DistributorView): Promise<void> {
  detail.value = await apiGet<DistributorDetail>(`/api/ui/v1/distributors/${distributor.partnerCode}`);
}

onMounted(search);
</script>

<template>
  <div class="page">
    <PageHeading title="Recherche distributeurs" subtitle="Par code partenaire ou raison sociale, dans vos entités." />

    <FilterBar :loading="loading" search-testid="search-button" @search="search" @reset="reset">
      <FormField label="Recherche" stacked width="long" v-slot="{ id }">
        <input :id="id" data-testid="search-term" type="text" v-model="term" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="search-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="results"
        :loading="loading"
        row-key="partnerCode"
        empty="Aucun distributeur."
        empty-testid="empty"
        clickable
        :row-testid="(d) => `distributor-row-${d.partnerCode}`"
        @row-click="open"
      >
        <template #cell-partnerCode="{ row }"><span class="mono">{{ row.partnerCode }}</span></template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="distributor-detail">
      <h2 class="ui-detail-title">{{ detail.legalName }} · {{ detail.partnerCode }}</h2>
      <DetailList>
        <DetailRow label="Groupement">{{ detail.groupCode ?? '—' }}</DetailRow>
      </DetailList>

      <h3 class="ui-detail-sub">Statut par entité</h3>
      <DataTable :columns="statusColumns" :rows="detail.statuses" empty="Aucun statut." :row-testid="() => 'status-row'" />

      <h3 class="ui-detail-sub">Codes de production</h3>
      <DataTable
        :columns="codeColumns"
        :rows="detail.productionCodes"
        empty="Aucun code de production."
        empty-testid="no-codes"
        :row-testid="(c) => `code-row-${c.code}`"
      >
        <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.ui-detail-title {
  margin: 0;
  font-size: var(--fs-h2);
}
.ui-detail-sub {
  margin: 0;
  font-size: var(--fs-h3);
  font-weight: 600;
}
</style>
