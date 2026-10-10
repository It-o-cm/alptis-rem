<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { cutoffViewLabel } from '../presentation/reporting';
import { formatAmount } from '../presentation/format';
import BarChart from '../components/charts/BarChart.vue';

interface Row {
  label: string;
  forecast: number;
  earned: number;
  adjustments: number;
  paid: number;
  balance: number;
  remaining: number;
}

interface Report {
  view: string;
  rows: Row[];
  total: Row;
}

const VIEWS = ['COVERAGE', 'DISTRIBUTOR', 'PRODUCT', 'ENTITY'];

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const date = ref('2026-09-30');
const view = ref('ENTITY');
const report = ref<Report | null>(null);
const error = ref('');

function viewLabel(v: string): string {
  return cutoffViewLabel(v);
}

function amount(value: number): string {
  return formatAmount(value);
}

async function load() {
  error.value = '';
  try {
    report.value = await apiGet<Report>(
      `/api/ui/v1/accounting/${entity.value}/cutoff?date=${date.value}&view=${view.value}`,
    );
  } catch (e) {
    report.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

function pick(v: string) {
  view.value = v;
  load();
}

const cutoffCategories = computed(() => (report.value ? report.value.rows.map((r) => r.label) : []));
const cutoffSeries = computed(() => {
  if (!report.value) {
    return [];
  }
  const rows = report.value.rows;
  return [
    { key: 'earned', label: 'Acquis', values: rows.map((r) => r.earned) },
    { key: 'paid', label: 'Payé', values: rows.map((r) => r.paid) },
    { key: 'balance', label: 'Solde à payer', values: rows.map((r) => r.balance) },
    { key: 'remaining', label: 'Reste à acquérir', values: rows.map((r) => r.remaining) },
  ];
});

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">État de cut-off</h1>
      <div class="page__subtitle">
        Ce qui était prévu, ce qui est acquis, ce qui a été payé et ce qui reste, à une date d'arrêté. Les
        deux dernières colonnes sont calculées (section 10.4).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="cutoff-entity" type="text" v-model="entity" /></label>
        <label>Date d'arrêté <input data-testid="cutoff-date" type="text" v-model="date" /></label>
        <button class="btn" data-testid="cutoff-refresh" @click="load">Rafraîchir</button>
      </div>
      <div style="display: flex; gap: 8px; align-items: center; margin-top: 12px; flex-wrap: wrap">
        <span style="font-size: 14px; color: #4a5868">Lire par :</span>
        <button v-for="v in VIEWS" :key="v" class="chip" :class="{ on: view === v }"
                :data-testid="`cutoff-view-${v}`" @click="pick(v)">{{ viewLabel(v) }}</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="cutoff-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <section v-if="report && report.rows.length > 0" class="card">
      <BarChart mode="stacked" :categories="cutoffCategories" :series="cutoffSeries"
                title="Acquis, payé, solde à payer et reste à acquérir" caption="en euros" testid="chart-cutoff" />
    </section>

    <section v-if="report" class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr>
            <th>{{ viewLabel(report.view) }}</th><th>Prévisionnel</th><th>Acquis cumulé</th>
            <th>Régularisations cumulées</th><th>Payé cumulé</th><th>Solde à payer</th><th>Reste à acquérir</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="report.rows.length === 0"><td colspan="7" data-testid="no-cutoff-row">Aucune ligne.</td></tr>
          <tr v-for="(r, i) in report.rows" :key="i" :data-testid="`cutoff-row-${i}`">
            <td>{{ r.label }}</td>
            <td>{{ amount(r.forecast) }}</td>
            <td>{{ amount(r.earned) }}</td>
            <td>{{ amount(r.adjustments) }}</td>
            <td>{{ amount(r.paid) }}</td>
            <td :data-testid="`cutoff-balance-${i}`">{{ amount(r.balance) }}</td>
            <td>{{ amount(r.remaining) }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td>Total affiché</td>
            <td data-testid="cutoff-total-forecast">{{ amount(report.total.forecast) }}</td>
            <td data-testid="cutoff-total-earned">{{ amount(report.total.earned) }}</td>
            <td>{{ amount(report.total.adjustments) }}</td>
            <td>{{ amount(report.total.paid) }}</td>
            <td>{{ amount(report.total.balance) }}</td>
            <td>{{ amount(report.total.remaining) }}</td>
          </tr>
        </tfoot>
      </table>
    </section>
  </div>
</template>
