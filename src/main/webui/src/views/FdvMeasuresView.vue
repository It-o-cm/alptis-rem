<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { measureTypeLabel } from '../presentation/salesforce';
import { formatAmount } from '../presentation/format';
import { AppCard, AppMessage, DataTable, EntitySelect, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface Measure {
  externalId: string;
  collaborator: string;
  measureType: string;
  dimension: string | null;
  periodFrom: string | null;
  periodTo: string | null;
  value: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const measures = ref<Measure[]>([]);
const error = ref('');
const loading = ref(false);

/** Columns of the measures table. */
const columns: Column<Measure>[] = [
  { key: 'externalId', label: 'Matricule' },
  { key: 'collaborator', label: 'Collaborateur' },
  { key: 'measureType', label: 'Mesure', value: (m) => measureTypeLabel(m.measureType) },
  { key: 'dimension', label: 'Détail', value: (m) => m.dimension ?? '—' },
  { key: 'period', label: 'Période', value: (m) => `${m.periodFrom ?? '—'} → ${m.periodTo ?? '—'}` },
  { key: 'value', label: 'Valeur', align: 'num', value: (m) => formatAmount(m.value) },
];

/** Loads the measures received for the selected entity. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    measures.value = await apiGet<Measure[]>(`/api/ui/v1/fdv/${entity.value}/measures`);
  } catch (e) {
    measures.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Mesures reçues"
      subtitle="Données de mesure reçues pour la force de vente (FDV-02) : absences hors congés et RTT, production par marché, activité commerciale du CRM, marge et cahiers des charges reçus. Elles sont alimentées par le SI RH et le CRM (flux d'import)."
    />

    <FilterBar :loading="loading" search-label="Rafraîchir" search-testid="fdvm-refresh" :resettable="false" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="fdvm-entity" v-model="entity" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="fdvm-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="measures"
        :loading="loading"
        :row-key="(m) => `${m.externalId}-${m.measureType}-${m.periodFrom}`"
        empty="Aucune mesure reçue."
        empty-testid="no-measure"
        :row-testid="(m) => `measure-row-${m.externalId}-${m.measureType}-${m.periodFrom}`"
      >
        <template #cell-externalId="{ row }"><span class="mono">{{ row.externalId }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>
