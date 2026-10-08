<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';

interface ExternalCodeView {
  objectType: string;
  internalCode: string | null;
  sourceSystem: string;
  externalCode: string;
}

const rows = ref<ExternalCodeView[]>([]);
const error = ref('');

async function load() {
  error.value = '';
  try {
    rows.value = await apiGet<ExternalCodeView[]>('/api/ui/v1/external-codes');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Codes externes</h1>
      <div class="page__subtitle">Correspondance des identifiants internes par système source (REF-04).</div>
    </div>
    <div v-if="error" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">{{ error }}</div>
    <section v-else class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Objet</th><th>Code interne</th><th>Système source</th><th>Code externe</th></tr></thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="4" data-testid="empty">Aucune correspondance.</td></tr>
          <tr v-for="(c, i) in rows" :key="i" :data-testid="`code-row-${i}`">
            <td>{{ c.objectType }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ c.internalCode ?? '—' }}</td>
            <td>{{ c.sourceSystem }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ c.externalCode }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
