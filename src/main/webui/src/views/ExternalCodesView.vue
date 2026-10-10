<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface ExternalCodeView {
  objectType: string;
  internalCode: string | null;
  sourceSystem: string;
  externalCode: string;
}

const rows = ref<ExternalCodeView[]>([]);
const error = ref('');
const loading = ref(false);

/** Columns of the external-codes list. */
const columns: Column<ExternalCodeView>[] = [
  { key: 'objectType', label: 'Objet' },
  { key: 'internalCode', label: 'Code interne', value: (c) => c.internalCode ?? '—' },
  { key: 'sourceSystem', label: 'Système source' },
  { key: 'externalCode', label: 'Code externe' },
];

/** Loads the external-code correspondences of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<ExternalCodeView[]>('/api/ui/v1/external-codes');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Codes externes"
      subtitle="Correspondance des identifiants internes par système source (REF-04)."
    />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucune correspondance."
        empty-testid="empty"
        :row-testid="(c) => `code-row-${rows.indexOf(c)}`"
      >
        <template #cell-internalCode="{ row }"><span class="mono">{{ row.internalCode ?? '—' }}</span></template>
        <template #cell-externalCode="{ row }"><span class="mono">{{ row.externalCode }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>
