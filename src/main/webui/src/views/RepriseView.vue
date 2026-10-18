<script setup lang="ts">
import { ref } from 'vue';
import { apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateImports } from '../presentation/access';
import { importStatusLabel } from '../presentation/imports';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, EntitySelect, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface UploadResponse {
  id: number;
  status: string;
  linesRead: number;
  linesFiltered: number;
  linesLoaded: number;
  linesRejected: number;
  linesOnHold: number;
}

/** The opening datasets of the history reprise (ING-11), each with its flow code and column header. */
const DATASETS = [
  { flow: 'opening_balances', label: 'Soldes des comptes', header: 'payee_type;payee_code;opening_balance\n' },
  {
    flow: 'opening_advances',
    label: "Positions d'escompte",
    header: 'coverage_code;advanced;total_due;settlement_due_date\n',
  },
  {
    flow: 'opening_counters',
    label: 'Cumuls annuels des surcommissions',
    header: 'rule_code;payee_type;payee_code;year;month;cumulative_basis;cumulative_entitlement\n',
  },
];

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const sourceSystem = ref('REPRISE');
const dataset = ref(DATASETS[0].flow);
const content = ref(DATASETS[0].header);
const result = ref<UploadResponse | null>(null);
const error = ref('');
const uploading = ref(false);

const canUpload = () => canOperateImports(session.roles);

/** Resets the editable content to the chosen dataset's column header. */
function onDatasetChange(): void {
  content.value = DATASETS.find((d) => d.flow === dataset.value)!.header;
}

/** Columns of the upload-result table. */
const columns: Column<UploadResponse>[] = [
  { key: 'linesRead', label: 'Lues', align: 'num' },
  { key: 'linesFiltered', label: 'Filtrées', align: 'num' },
  { key: 'linesLoaded', label: 'Chargées', align: 'num' },
  { key: 'linesRejected', label: 'Rejetées', align: 'num' },
  { key: 'linesOnHold', label: 'En attente', align: 'num' },
  { key: 'status', label: 'Statut', value: (r) => importStatusLabel(r.status) },
];

/** Uploads the opening-balance file for the selected entity and source system. */
async function upload(): Promise<void> {
  error.value = '';
  uploading.value = true;
  try {
    result.value = await apiPost<UploadResponse>('/api/ui/v1/imports', {
      entityCode: entity.value,
      flowCode: dataset.value,
      sourceSystem: sourceSystem.value,
      fileName: `${dataset.value}.csv`,
      content: content.value,
    });
  } catch (e) {
    result.value = null;
    error.value = e instanceof ApiError ? e.message : 'Dépôt impossible';
  } finally {
    uploading.value = false;
  }
}
</script>

<template>
  <div class="page">
    <PageHeading
      title="Reprise d'historique"
      subtitle="Dépôt des fichiers d'ouverture à la bascule d'un périmètre, lot par lot : soldes des comptes (écriture d'ouverture), positions d'escompte en cours et cumuls annuels des surcommissions, contrôlés ligne à ligne ; la première période est calculée dessus (ING-11)."
    />

    <AppCard v-if="canUpload()" data-testid="reprise-form">
      <AppForm @submit="upload">
        <FormField label="Entité" width="short" v-slot="{ id }">
          <EntitySelect :id="id" data-testid="reprise-entity" v-model="entity" />
        </FormField>
        <FormField label="Jeu d'ouverture" width="short" v-slot="{ id }">
          <select :id="id" data-testid="reprise-dataset" v-model="dataset" @change="onDatasetChange">
            <option v-for="d in DATASETS" :key="d.flow" :value="d.flow">{{ d.label }}</option>
          </select>
        </FormField>
        <FormField label="Système source" width="short" v-slot="{ id }">
          <input :id="id" data-testid="reprise-source" type="text" v-model="sourceSystem" />
        </FormField>
        <FormField label="Fichier d'ouverture (CSV)" width="full" v-slot="{ id }">
          <textarea :id="id" class="code-area" data-testid="reprise-content" v-model="content" rows="8"></textarea>
        </FormField>
        <AppButton type="submit" :loading="uploading" data-testid="reprise-upload">Déposer</AppButton>
      </AppForm>
    </AppCard>

    <AppMessage v-if="error" variant="error" data-testid="reprise-error">{{ error }}</AppMessage>

    <AppCard v-if="result" flush data-testid="reprise-result">
      <DataTable :columns="columns" :rows="[result]">
        <template #cell-linesLoaded="{ row }"><span data-testid="reprise-loaded">{{ row.linesLoaded }}</span></template>
        <template #cell-linesRejected="{ row }"><span data-testid="reprise-rejected">{{ row.linesRejected }}</span></template>
        <template #cell-linesOnHold="{ row }"><span data-testid="reprise-onhold">{{ row.linesOnHold }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.code-area {
  width: 100%;
  font-family: 'IBM Plex Mono', monospace;
  font-size: var(--fs-small);
}
</style>
