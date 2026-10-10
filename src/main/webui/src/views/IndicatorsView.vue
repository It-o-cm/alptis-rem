<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount, formatPercent } from '../presentation/format';

interface IndicatorMonth {
  month: string;
  activeRules: number;
  cycleDuration: number;
  manualShare: number;
  clawback: number;
}
interface Indicators {
  entity: string;
  months: string[];
  rows: IndicatorMonth[];
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const from = ref('');
const to = ref('');
const data = ref<Indicators | null>(null);
const csv = ref('');
const error = ref('');

function query(): string {
  const params = new URLSearchParams();
  if (from.value) {
    params.set('from', from.value);
  }
  if (to.value) {
    params.set('to', to.value);
  }
  const q = params.toString();
  return q ? `?${q}` : '';
}

async function load() {
  error.value = '';
  csv.value = '';
  try {
    data.value = await apiGet<Indicators>(`/api/ui/v1/reporting/${entity.value}/indicators${query()}`);
  } catch (e) {
    data.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function exportCsv() {
  csv.value = await apiGetText(`/api/ui/v1/reporting/${entity.value}/export/indicators${query()}`);
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Indicateurs de fonctionnement</h1>
      <div class="page__subtitle">
        Règles actives, durée du cycle, part des lignes ajustées à la main et montants repris, mois par mois (REP-09).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="ind-entity" type="text" v-model="entity" /></label>
        <label>Du mois <input data-testid="ind-from" type="text" v-model="from" placeholder="2026-06-01" /></label>
        <label>Au mois <input data-testid="ind-to" type="text" v-model="to" placeholder="2026-11-01" /></label>
        <button class="btn" data-testid="ind-refresh" @click="load">Rafraîchir</button>
        <button class="btn sec" data-testid="ind-export" @click="exportCsv">Exporter CSV</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="ind-error" style="margin-top: 10px">{{ error }}</div>
      <pre v-if="csv" data-testid="ind-export-content" style="margin-top: 10px; overflow-x: auto; background: #f6f8fa; padding: 12px">{{ csv }}</pre>
    </section>

    <section v-if="data" class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Mois</th><th>Règles actives</th><th>Durée du cycle (jours)</th><th>Lignes manuelles</th><th>Montants repris</th></tr>
        </thead>
        <tbody>
          <tr v-if="data.rows.length === 0"><td colspan="5" data-testid="ind-empty">Aucune donnée.</td></tr>
          <tr v-for="(r, i) in data.rows" :key="i" :data-testid="`ind-row-${i}`">
            <td>{{ r.month }}</td>
            <td class="num" :data-testid="`ind-rules-${i}`">{{ r.activeRules }}</td>
            <td class="num">{{ r.cycleDuration }}</td>
            <td class="num">{{ formatPercent(r.manualShare) }}</td>
            <td class="num" :data-testid="`ind-clawback-${i}`">{{ formatAmount(r.clawback) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
