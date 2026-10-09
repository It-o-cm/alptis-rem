<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';

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

async function load() {
  error.value = '';
  try {
    reference.value = await apiGet<Reference>('/api/ui/v1/flows/formats');
  } catch (e) {
    reference.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Formats d'échange</h1>
      <div class="page__subtitle">
        Les principaux fichiers échangés et les conventions communes des fichiers CSV (section 9.9).
      </div>
    </div>

    <div v-if="error" class="tag ko" data-testid="formats-error">{{ error }}</div>

    <section v-if="reference" class="card" style="overflow-x: auto">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Fichiers</h2>
      <table>
        <thead><tr><th>Flux</th><th>Contenu</th><th>Format</th><th>Nom de fichier</th></tr></thead>
        <tbody>
          <tr v-for="(f, i) in reference.files" :key="i" :data-testid="`format-file-${i}`">
            <td>{{ f.flow }}</td>
            <td>{{ f.content }}</td>
            <td>{{ f.format }}</td>
            <td>{{ f.fileName }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="reference" class="card" style="padding: 18px 20px">
      <h2 style="margin: 0 0 10px; font-size: 16px">Conventions communes</h2>
      <ul style="margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: 6px; font-size: 14px">
        <li v-for="(c, i) in reference.conventions" :key="i" :data-testid="`format-convention-${i}`">{{ c }}</li>
      </ul>
    </section>
  </div>
</template>
