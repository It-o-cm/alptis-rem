<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';

interface Health {
  status: string;
  pendingEvents: number;
  heldEvents: number;
  openPeriods: number;
  lastImportAt: string | null;
  lastExportAt: string | null;
  lastCalcAt: string | null;
}

const data = ref<Health | null>(null);
const error = ref('');

function at(value: string | null): string {
  return value ?? '—';
}

async function load() {
  error.value = '';
  try {
    data.value = await apiGet<Health>('/api/ui/v1/admin/supervision');
  } catch (e) {
    data.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Supervision des traitements et des flux</h1>
      <div class="page__subtitle">
        État de santé et métriques des traitements : événements en attente, périodes ouvertes et derniers
        échanges (OUT-07, NFR-16).
      </div>
    </div>

    <div v-if="error" class="tag ko" data-testid="sup-error">{{ error }}</div>

    <template v-if="data">
      <div style="display: flex; gap: 10px; align-items: center">
        <span class="tag ok" data-testid="sup-status">{{ data.status }}</span>
      </div>
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px">
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Événements en attente</div>
          <div data-testid="sup-pending" style="font: 500 22px monospace; margin-top: 6px">{{ data.pendingEvents }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Événements gelés</div>
          <div style="font: 500 22px monospace; margin-top: 6px">{{ data.heldEvents }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Périodes ouvertes</div>
          <div style="font: 500 22px monospace; margin-top: 6px">{{ data.openPeriods }}</div></div>
      </div>
      <section class="card" style="overflow-x: auto">
        <table>
          <tbody>
            <tr><td>Dernier import</td><td data-testid="sup-import">{{ at(data.lastImportAt) }}</td></tr>
            <tr><td>Dernier export</td><td>{{ at(data.lastExportAt) }}</td></tr>
            <tr><td>Dernier calcul</td><td>{{ at(data.lastCalcAt) }}</td></tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
