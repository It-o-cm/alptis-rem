<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import HBarChart from '../components/charts/HBarChart.vue';

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
const month = ref('2026-09-01');
const rows = ref<Result[]>([]);
const error = ref('');
const message = ref('');
const adjust = ref({ externalId: '', amount: 0, reason: '' });

const canValidate = () => session.roles.includes('SALES_MANAGER') || session.roles.includes('BUSINESS_ADMIN');
const canExport = () => session.roles.includes('FINANCE_CONTROLLER');

function amount(value: number): string {
  return formatAmount(value);
}

function total(): number {
  return rows.value.reduce((sum, r) => sum + r.amount, 0);
}

const achievementData = computed(() => rows.value.map((r) => ({ label: r.employeeName, value: r.achievementRate })));

async function load() {
  error.value = '';
  try {
    rows.value = await apiGet<Result[]>(`/api/ui/v1/fdv/${entity.value}/${month.value}/results`);
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function validate(r: Result) {
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/fdv/results/${r.id}/validate`, {});
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Validation refusée';
  }
}

async function submitAdjustment() {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/fdv/${entity.value}/${month.value}/adjust`, {
      externalId: adjust.value.externalId,
      amount: adjust.value.amount,
      reason: adjust.value.reason,
    });
    message.value = 'Ajustement enregistré.';
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Ajustement refusé';
  }
}

async function payExport() {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ fileName: string; lines: number }>(
      `/api/ui/v1/fdv/${entity.value}/${month.value}/pay-export`, {});
    message.value = `Export paie produit : ${result.fileName} (${result.lines} lignes).`;
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Export refusé';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Validation mensuelle du variable</h1>
      <div class="page__subtitle">
        Le responsable valide les montants, autorise un ajustement motivé, et la Finance exporte vers la
        paie une fois tout validé (FDV-13, FDV-14).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="fdvv-entity" type="text" v-model="entity" /></label>
        <label>Mois <input data-testid="fdvv-month" type="text" v-model="month" /></label>
        <button class="btn" data-testid="fdvv-refresh" @click="load">Rafraîchir</button>
        <button v-if="canExport()" class="btn" data-testid="fdvv-export" @click="payExport">Export paie</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="fdvv-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="fdvv-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <section v-if="canValidate()" class="card" style="padding: 18px 20px" data-testid="fdvv-adjust-form">
      <h2 style="margin: 0 0 12px; font-size: 16px">Ajustement motivé</h2>
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Collaborateur <input data-testid="fdvv-adj-id" type="text" v-model="adjust.externalId" /></label>
        <label>Montant <input data-testid="fdvv-adj-amount" type="number" v-model.number="adjust.amount" /></label>
        <label>Motif <input data-testid="fdvv-adj-reason" type="text" v-model="adjust.reason" /></label>
        <button class="btn" data-testid="fdvv-adj-submit" @click="submitAdjustment">Enregistrer</button>
      </div>
    </section>

    <section v-if="rows.length > 0" class="card">
      <HBarChart :data="achievementData" title="Atteinte de l'objectif par collaborateur" format="percent"
                 testid="chart-fdvv" />
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Collaborateur</th><th>Poste</th><th>Composante</th><th>Atteinte</th><th>Montant</th><th>Statut</th><th>Motif</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="8" data-testid="no-result">Aucun résultat.</td></tr>
          <tr v-for="r in rows" :key="r.id" :data-testid="`result-row-${r.id}`">
            <td>{{ r.employeeName }}</td>
            <td>{{ r.jobCode }}</td>
            <td>{{ r.component }}</td>
            <td>{{ (r.achievementRate * 100).toFixed(0) }} %</td>
            <td>{{ amount(r.amount) }}</td>
            <td :data-testid="`result-status-${r.id}`">{{ r.status }}</td>
            <td>{{ r.adjustmentReason ?? '—' }}</td>
            <td>
              <button v-if="canValidate() && r.status === 'CALCULATED'" class="btn"
                      :data-testid="`result-validate-${r.id}`" @click="validate(r)">Valider</button>
            </td>
          </tr>
        </tbody>
        <tfoot>
          <tr><td colspan="4">Total à payer</td><td data-testid="fdvv-total">{{ amount(total()) }}</td><td colspan="3"></td></tr>
        </tfoot>
      </table>
    </section>
  </div>
</template>
