<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { flowDirectionLabel } from '../presentation/reporting';

interface Flow {
  id: string;
  direction: string;
  system: string;
  content: string;
  format: string;
  frequency: string;
}

const FILTERS = [
  { key: '', label: 'Tous les flux' },
  { key: 'INBOUND', label: 'Entrants' },
  { key: 'OUTBOUND', label: 'Sortants' },
];

const rows = ref<Flow[]>([]);
const filter = ref('');
const error = ref('');

function directionLabel(d: string): string {
  return flowDirectionLabel(d);
}

async function load() {
  error.value = '';
  try {
    const q = filter.value ? `?direction=${filter.value}` : '';
    rows.value = await apiGet<Flow[]>(`/api/ui/v1/flows/catalog${q}`);
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

function pick(key: string) {
  filter.value = key;
  load();
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Catalogue des flux</h1>
      <div class="page__subtitle">
        Tout ce que l'application reçoit et tout ce qu'elle émet : système, contenu, format, transport et
        fréquence (section 9.1).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 8px; flex-wrap: wrap">
        <button v-for="f in FILTERS" :key="f.key" class="chip" :class="{ on: filter === f.key }"
                :data-testid="`flux-filter-${f.key || 'all'}`" @click="pick(f.key)">{{ f.label }}</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="flux-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>N°</th><th>Sens</th><th>Système</th><th>Contenu</th><th>Format et transport</th><th>Fréquence</th></tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="6" data-testid="no-flow">Aucun flux.</td></tr>
          <tr v-for="f in rows" :key="f.id" :data-testid="`flow-row-${f.id}`">
            <td>{{ f.id }}</td>
            <td :data-testid="`flow-direction-${f.id}`">{{ directionLabel(f.direction) }}</td>
            <td>{{ f.system }}</td>
            <td>{{ f.content }}</td>
            <td>{{ f.format }}</td>
            <td>{{ f.frequency }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
