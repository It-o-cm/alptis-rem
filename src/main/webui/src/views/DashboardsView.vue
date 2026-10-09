<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';

interface Amount {
  label: string;
  amount: number;
}

interface Dashboard {
  entity: string;
  byNature: Amount[];
  reprises: number;
  escomptesVerses: number;
  escomptesApures: number;
  debtorAccounts: number;
  topDistributors: Amount[];
  anomalies: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const month = ref('2026-09-01');
const data = ref<Dashboard | null>(null);
const error = ref('');

function amount(value: number): string {
  return formatAmount(value);
}

async function load() {
  error.value = '';
  try {
    const q = month.value ? `?month=${month.value}` : '';
    data.value = await apiGet<Dashboard>(`/api/ui/v1/reporting/${entity.value}/dashboard${q}`);
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
      <h1 class="page__title" data-testid="page-title">Tableaux de bord</h1>
      <div class="page__subtitle">
        Commissions par nature, reprises, escomptes, comptes débiteurs, premiers distributeurs et anomalies
        de l'entité (REP-01).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="dash-entity" type="text" v-model="entity" /></label>
        <label>Mois <input data-testid="dash-month" type="text" v-model="month" /></label>
        <button class="btn" data-testid="dash-refresh" @click="load">Rafraîchir</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="dash-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <template v-if="data">
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px">
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Reprises</div>
          <div data-testid="dash-reprises" style="font: 500 22px monospace; margin-top: 6px">{{ amount(data.reprises) }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Escomptes versés</div>
          <div data-testid="dash-verses" style="font: 500 22px monospace; margin-top: 6px">{{ amount(data.escomptesVerses) }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Escomptes apurés</div>
          <div style="font: 500 22px monospace; margin-top: 6px">{{ amount(data.escomptesApures) }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Comptes débiteurs</div>
          <div data-testid="dash-debtors" style="font: 500 22px monospace; margin-top: 6px">{{ data.debtorAccounts }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Anomalies</div>
          <div style="font: 500 22px monospace; margin-top: 6px">{{ data.anomalies }}</div></div>
      </div>

      <section class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Commissions par nature</h2>
        <table>
          <thead><tr><th>Nature</th><th>Montant</th></tr></thead>
          <tbody>
            <tr v-if="data.byNature.length === 0"><td colspan="2" data-testid="no-nature">Aucune commission.</td></tr>
            <tr v-for="(n, i) in data.byNature" :key="i" :data-testid="`dash-nature-${n.label}`">
              <td>{{ n.label }}</td><td>{{ amount(n.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Premiers distributeurs</h2>
        <table>
          <thead><tr><th>Distributeur</th><th>Montant</th></tr></thead>
          <tbody>
            <tr v-for="(d, i) in data.topDistributors" :key="i" :data-testid="`dash-top-${i}`">
              <td>{{ d.label }}</td><td>{{ amount(d.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
