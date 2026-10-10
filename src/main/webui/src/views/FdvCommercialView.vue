<script setup lang="ts">
import { ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import GaugeChart from '../components/charts/GaugeChart.vue';
import { AppCard, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface Result {
  id: number;
  employee: string;
  employeeName: string;
  jobCode: string;
  component: string;
  achievementRate: number;
  amount: number;
  status: string;
  adjustmentReason: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const externalId = ref('');
const rows = ref<Result[]>([]);
const error = ref('');
const loaded = ref(false);
const loading = ref(false);

/**
 * Formats a monetary value in euros.
 * @param value the amount to format
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Columns of the commercial's own variable table. */
const columns: Column<Result>[] = [
  { key: 'component', label: 'Composante' },
  { key: 'achievementRate', label: 'Atteinte', align: 'num', value: (r) => `${(r.achievementRate * 100).toFixed(0)} %` },
  { key: 'amount', label: 'Montant', align: 'num', value: (r) => amount(r.amount) },
  { key: 'status', label: 'Statut' },
  { key: 'adjustmentReason', label: 'Motif', value: (r) => r.adjustmentReason ?? '—' },
];

/** Loads the commercial's own variable for the given matriculation. */
async function load(): Promise<void> {
  error.value = '';
  loaded.value = false;
  loading.value = true;
  try {
    rows.value = await apiGet<Result[]>(`/api/ui/v1/fdv/${entity.value}/my-variable/${externalId.value}`);
    loaded.value = true;
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="page">
    <PageHeading title="Mon variable" subtitle="Mon variable, mes objectifs et mon historique mois par mois (FDV-15)." />

    <FilterBar :loading="loading" :resettable="false" search-label="Afficher" search-testid="fdvc-load" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="fdvc-entity" type="text" v-model="entity" />
      </FormField>
      <FormField label="Matricule" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="fdvc-id" type="text" v-model="externalId" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="fdvc-error">{{ error }}</AppMessage>

    <div v-if="loaded && rows.length > 0" class="fdvc-gauges">
      <AppCard v-for="r in rows" :key="`g${r.id}`">
        <GaugeChart :title="`Atteinte — ${r.component}`" :caption="r.employeeName" :value="r.achievementRate"
                    :target="1" :testid="`chart-gauge-${r.id}`" />
      </AppCard>
    </div>

    <AppCard v-if="loaded" flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        row-key="id"
        empty="Aucun variable."
        empty-testid="no-my-result"
        :row-testid="(r) => `my-result-${r.id}`"
      />
    </AppCard>
  </div>
</template>

<style scoped>
.fdvc-gauges {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--space-4);
}
</style>
