<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppCard, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface CoverageView {
  coverageCode: string;
  status: string | null;
  annualPremium: string | null;
}

interface ContractView {
  entityCode: string;
  contractNumber: string;
  sourceSystem: string;
  status: string | null;
  coverages: CoverageView[];
}

const term = ref('');
const results = ref<ContractView[]>([]);
const error = ref('');
const loading = ref(false);

/** Columns of the contracts results table. */
const columns: Column<ContractView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'contractNumber', label: 'Adhésion' },
  { key: 'sourceSystem', label: 'Source' },
  { key: 'status', label: 'Statut', value: (c) => c.status ?? '—' },
  { key: 'coverages', label: 'Garanties' },
];

/** Searches contracts by membership number within the user's entities. */
async function search(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    const page = await apiGet<{ items: ContractView[] }>(`/api/ui/v1/contracts?q=${encodeURIComponent(term.value)}`);
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
  error.value = '';
}

onMounted(search);
</script>

<template>
  <div class="page">
    <PageHeading title="Recherche contrats" subtitle="Adhésions et garanties, dans vos entités." />

    <FilterBar :loading="loading" search-testid="search-button" @search="search" @reset="reset">
      <FormField label="Numéro d'adhésion" stacked width="long" v-slot="{ id }">
        <input :id="id" data-testid="search-term" type="text" v-model="term" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="search-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="results"
        :loading="loading"
        :row-key="(c) => c.entityCode + c.sourceSystem + c.contractNumber"
        empty="Aucune adhésion."
        empty-testid="empty"
        :row-testid="(c) => `contract-row-${c.contractNumber}`"
      >
        <template #cell-contractNumber="{ row }"><span class="mono">{{ row.contractNumber }}</span></template>
        <template #cell-coverages="{ row }">
          <span v-for="cov in row.coverages" :key="cov.coverageCode" class="tag info ui-cov">
            {{ cov.coverageCode }}<span v-if="cov.annualPremium"> · {{ cov.annualPremium }}</span>
          </span>
          <span v-if="row.coverages.length === 0">—</span>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.ui-cov {
  margin-right: var(--space-1);
}
</style>
