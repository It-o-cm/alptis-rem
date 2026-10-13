<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canProduceExport, exportStatusLabel } from '../presentation/reporting';
import { formatAmount } from '../presentation/format';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface ExportRow {
  id: number;
  flowCode: string;
  fileName: string;
  fileHash: string;
  author: string;
  lines: number;
  total: number;
  status: string;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const rows = ref<ExportRow[]>([]);
const content = ref('');
const error = ref('');
const message = ref('');
const loading = ref(false);
const form = ref({ object: 'COMMISSION_LINES', period: '2026-09-01', data: false });

const canProduce = () => canProduceExport(session.roles);

/** Columns of the exports journal table. */
const columns: Column<ExportRow>[] = [
  { key: 'flowCode', label: 'Flux' },
  { key: 'fileName', label: 'Fichier' },
  { key: 'fileHash', label: 'Empreinte' },
  { key: 'author', label: 'Auteur' },
  { key: 'lines', label: 'Lignes', align: 'num' },
  { key: 'total', label: 'Total', align: 'num', value: (r) => amount(r.total) },
  { key: 'status', label: 'État' },
];

/**
 * French label of an export status.
 * @param status the status code
 */
function statusLabel(status: string): string {
  return exportStatusLabel(status);
}

/**
 * Formats a monetary amount in euros.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Loads the exports journal of the selected entity. */
async function load(): Promise<void> {
  error.value = '';
  content.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<ExportRow[]>(`/api/ui/v1/flows/${entity.value}/exports`);
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/** Produces a new export for the selected object and period. */
async function produce(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    const q = form.value.data ? '' : `&period=${form.value.period}`;
    await apiPost(`/api/ui/v1/flows/${entity.value}/exports?object=${form.value.object}${q}`, {});
    message.value = 'Export produit.';
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Production refusée';
  }
}

/**
 * Downloads and displays the content of an export.
 * @param row the export to download
 */
async function download(row: ExportRow): Promise<void> {
  error.value = '';
  try {
    content.value = await apiGetText(`/api/ui/v1/flows/exports/${row.id}/content`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Téléchargement impossible';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Exports de la période"
      subtitle="Chaque fichier émis est inscrit au journal des exports avec son empreinte, son nombre de lignes et son total : on sait toujours ce qui est parti, quand, vers qui et avec quel contenu (section 9.11)."
    />

    <FilterBar
      :loading="loading"
      search-label="Rafraîchir"
      search-testid="exports-refresh"
      :resettable="false"
      @search="load"
    >
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="exports-entity" type="text" v-model="entity" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="exports-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="exports-message">{{ message }}</AppMessage>

    <AppCard v-if="canProduce()" title="Produire un export" data-testid="exports-form">
      <AppForm @submit="produce">
        <FormField label="Objet" width="short" v-slot="{ id }">
          <select :id="id" data-testid="exports-object" v-model="form.object">
            <option value="COMMISSION_LINES">Lignes de commission</option>
            <option value="ACCOUNTS">Comptes et soldes</option>
            <option value="CONTROL_RESULTS">Données des contrôles (niveaux 2 et 3)</option>
          </select>
        </FormField>
        <FormField label="Période" width="date" v-slot="{ id }">
          <input :id="id" data-testid="exports-period" type="text" v-model="form.period" :disabled="form.data" />
        </FormField>
        <FormField label="Export Data (sans période)" width="short" v-slot="{ id }">
          <input :id="id" data-testid="exports-data" type="checkbox" v-model="form.data" />
        </FormField>
        <AppButton type="submit" data-testid="exports-produce">Produire</AppButton>
      </AppForm>
    </AppCard>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        row-key="id"
        empty="Aucun export."
        empty-testid="no-export"
        :row-testid="(r) => `export-row-${r.id}`"
      >
        <template #cell-fileHash="{ row }"><span class="mono">{{ row.fileHash.slice(0, 12) }}…</span></template>
        <template #cell-status="{ row }">
          <span :data-testid="`export-status-${row.id}`">{{ statusLabel(row.status) }}</span>
        </template>
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`export-download-${row.id}`" @click="download(row)">
            Télécharger
          </AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="content" title="Contenu" data-testid="export-content-card">
      <pre class="export-content" data-testid="export-content">{{ content }}</pre>
    </AppCard>
  </div>
</template>

<style scoped>
.export-content {
  overflow-x: auto;
  background: var(--line-soft);
  padding: var(--space-3);
  border-radius: var(--radius);
  margin: 0;
}
</style>
