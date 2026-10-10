<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface FormatFile {
  flow: string;
  content: string;
  format: string;
  fileName: string;
}

interface Reference {
  files: FormatFile[];
  conventions: string[];
}

const reference = ref<Reference | null>(null);
const error = ref('');
const loading = ref(false);

/** Columns of the exchanged-files table. */
const columns: Column<FormatFile>[] = [
  { key: 'flow', label: 'Flux' },
  { key: 'content', label: 'Contenu' },
  { key: 'format', label: 'Format' },
  { key: 'fileName', label: 'Nom de fichier' },
];

/** Loads the exchange-format reference. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    reference.value = await apiGet<Reference>('/api/ui/v1/flows/formats');
  } catch (e) {
    reference.value = null;
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
      title="Formats d'échange"
      subtitle="Les principaux fichiers échangés et les conventions communes des fichiers CSV (section 9.9)."
    />

    <AppMessage v-if="error" variant="error" data-testid="formats-error">{{ error }}</AppMessage>

    <AppCard v-if="reference" title="Fichiers">
      <DataTable
        :columns="columns"
        :rows="reference.files"
        :loading="loading"
        empty="Aucun fichier."
        :row-testid="(f) => `format-file-${reference!.files.indexOf(f)}`"
      />
    </AppCard>

    <AppCard v-if="reference" title="Conventions communes">
      <ul class="formats-conventions">
        <li v-for="(c, i) in reference.conventions" :key="i" :data-testid="`format-convention-${i}`">{{ c }}</li>
      </ul>
    </AppCard>
  </div>
</template>

<style scoped>
.formats-conventions {
  margin: 0;
  padding-left: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--fs-body);
}
</style>
