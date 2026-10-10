<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import HBarChart from '../components/charts/HBarChart.vue';

function marginData(rows: MarginRow[]): { label: string; value: number }[] {
  return rows.map((r) => ({ label: r.key, value: r.margin }));
}

interface MarginRow {
  key: string;
  received: number;
  paid: number;
  margin: number;
}
interface Margin {
  byProduct: MarginRow[];
  byInsurer: MarginRow[];
  byDistributor: MarginRow[];
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const data = ref<Margin | null>(null);
const csv = ref('');
const error = ref('');

function amount(value: number): string {
  return formatAmount(value);
}

async function load() {
  error.value = '';
  csv.value = '';
  try {
    data.value = await apiGet<Margin>(`/api/ui/v1/reporting/${entity.value}/margin`);
  } catch (e) {
    data.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function exportCsv() {
  csv.value = await apiGetText(`/api/ui/v1/reporting/${entity.value}/export/margin`);
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Marge</h1>
      <div class="page__subtitle">
        Marge (commission reçue − commission payée) par produit, par assureur et par distributeur (REP-08).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="margin-entity" type="text" v-model="entity" /></label>
        <button class="btn" data-testid="margin-refresh" @click="load">Rafraîchir</button>
        <button class="btn sec" data-testid="margin-export" @click="exportCsv">Exporter CSV</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="margin-error" style="margin-top: 10px">{{ error }}</div>
      <pre v-if="csv" data-testid="margin-export-content" style="margin-top: 10px; overflow-x: auto; background: #f6f8fa; padding: 12px">{{ csv }}</pre>
    </section>

    <template v-if="data">
      <section v-for="axis in [
          { id: 'product', label: 'Par produit', rows: data.byProduct },
          { id: 'insurer', label: 'Par assureur', rows: data.byInsurer },
          { id: 'distributor', label: 'Par distributeur', rows: data.byDistributor },
        ]" :key="axis.id" class="card" style="overflow-x: auto">
        <HBarChart :data="marginData(axis.rows)" :title="`Marge ${axis.label.toLowerCase()}`"
                   caption="en euros" :testid="`chart-margin-${axis.id}`" />
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">{{ axis.label }}</h2>
        <table>
          <thead><tr><th>Clé</th><th>Reçu</th><th>Payé</th><th>Marge</th></tr></thead>
          <tbody>
            <tr v-if="axis.rows.length === 0"><td colspan="4" :data-testid="`margin-empty-${axis.id}`">Aucune donnée.</td></tr>
            <tr v-for="(r, i) in axis.rows" :key="i" :data-testid="`margin-${axis.id}-${i}`">
              <td>{{ r.key }}</td><td class="num">{{ amount(r.received) }}</td>
              <td class="num">{{ amount(r.paid) }}</td><td class="num">{{ amount(r.margin) }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
