<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';

interface AuditRow {
  occurredAt: string;
  userId: string;
  entityCode: string | null;
  action: string;
  objectType: string;
  objectId: number | null;
  beforeValue: string | null;
  afterValue: string | null;
  previousHash: string;
  hash: string;
}

interface Page {
  items: AuditRow[];
  page: number;
  size: number;
  total: number;
}

const rows = ref<AuditRow[]>([]);
const page = ref(0);
const size = ref(20);
const total = ref(0);
const loading = ref(true);
const loadError = ref('');

function shortHash(hash: string | null): string {
  return hash ? hash.substring(0, 12) + '…' : '';
}

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const result = await apiGet<Page>(`/api/ui/v1/audit?page=${page.value}&size=${size.value}`);
    rows.value = result.items;
    total.value = result.total;
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

function next() {
  if ((page.value + 1) * size.value < total.value) {
    page.value += 1;
    reload();
  }
}

function previous() {
  if (page.value > 0) {
    page.value -= 1;
    reload();
  }
}

onMounted(reload);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Piste d'audit</h1>
      <div class="page__subtitle">Toute modification manuelle : auteur, date, avant, après, chaînée par empreinte (ADM-04).</div>
    </div>

    <div v-if="loadError" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">{{ loadError }}</div>

    <section v-else class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Date</th><th>Auteur</th><th>Entité</th><th>Action</th><th>Objet</th><th>Avant</th><th>Après</th><th>Empreinte</th></tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0"><td colspan="8" data-testid="empty">Aucune trace.</td></tr>
          <tr v-for="(row, index) in rows" :key="index" data-testid="audit-row">
            <td>{{ row.occurredAt }}</td>
            <td data-testid="audit-user">{{ row.userId }}</td>
            <td data-testid="audit-entity">{{ row.entityCode ?? '—' }}</td>
            <td data-testid="audit-action">{{ row.action }}</td>
            <td>{{ row.objectType }} #{{ row.objectId }}</td>
            <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis">{{ row.beforeValue ?? '—' }}</td>
            <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis">{{ row.afterValue ?? '—' }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace" :title="row.hash">{{ shortHash(row.hash) }}</td>
          </tr>
        </tbody>
      </table>
      <div style="padding: 12px 18px; display: flex; gap: 12px; align-items: center">
        <button class="btn sec" data-testid="audit-prev" :disabled="page === 0" @click="previous">Précédent</button>
        <span data-testid="audit-page">Page {{ page + 1 }} · {{ total }} trace(s)</span>
        <button class="btn sec" data-testid="audit-next" :disabled="(page + 1) * size >= total" @click="next">Suivant</button>
      </div>
    </section>
  </div>
</template>
