<script setup lang="ts">
import { ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import GaugeChart from '../components/charts/GaugeChart.vue';

interface Result {
  id: number;
  employee: string;
  employeeName: string;
  jobCode: string;
  component: string;
  achievementRate: number;
  amount: number;
  status: string;
  adjustmentReason: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const externalId = ref('');
const rows = ref<Result[]>([]);
const error = ref('');
const loaded = ref(false);

function amount(value: number): string {
  return formatAmount(value);
}

async function load() {
  error.value = '';
  loaded.value = false;
  try {
    rows.value = await apiGet<Result[]>(`/api/ui/v1/fdv/${entity.value}/my-variable/${externalId.value}`);
    loaded.value = true;
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Mon variable</h1>
      <div class="page__subtitle">
        Mon variable, mes objectifs et mon historique mois par mois (FDV-15).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="fdvc-entity" type="text" v-model="entity" /></label>
        <label>Matricule <input data-testid="fdvc-id" type="text" v-model="externalId" /></label>
        <button class="btn" data-testid="fdvc-load" @click="load">Afficher</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="fdvc-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <div v-if="loaded && rows.length > 0" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 14px">
      <section v-for="r in rows" :key="`g${r.id}`" class="card">
        <GaugeChart :title="`Atteinte — ${r.component}`" :caption="r.employeeName" :value="r.achievementRate"
                    :target="1" :testid="`chart-gauge-${r.id}`" />
      </section>
    </div>

    <section v-if="loaded" class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Composante</th><th>Atteinte</th><th>Montant</th><th>Statut</th><th>Motif</th></tr></thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="5" data-testid="no-my-result">Aucun variable.</td></tr>
          <tr v-for="r in rows" :key="r.id" :data-testid="`my-result-${r.id}`">
            <td>{{ r.component }}</td>
            <td>{{ (r.achievementRate * 100).toFixed(0) }} %</td>
            <td>{{ amount(r.amount) }}</td>
            <td>{{ r.status }}</td>
            <td>{{ r.adjustmentReason ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
