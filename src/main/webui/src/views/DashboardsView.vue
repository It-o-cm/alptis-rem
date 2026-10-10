<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';

interface Amount {
  label: string;
  amount: number;
}
interface MonthNature {
  month: string;
  nature: string;
  amount: number;
}
interface MonthValue {
  month: string;
  primary: number;
  second: number | null;
  third: number | null;
}
interface Dashboard {
  entity: string;
  from: string | null;
  to: string | null;
  months: string[];
  byNature: Amount[];
  byGamme: Amount[];
  byMonthNature: MonthNature[];
  reprisesByMonth: MonthValue[];
  escompteByMonth: MonthValue[];
  reprises: number;
  escomptesVerses: number;
  escomptesApures: number;
  creance: number;
  debtorAccounts: number;
  topDistributors: Amount[];
  anomalies: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const from = ref('');
const to = ref('');
const product = ref('');
const distributor = ref('');
const group = ref('');
const delegation = ref('');
const nature = ref('');
const data = ref<Dashboard | null>(null);
const csv = ref('');
const error = ref('');

function amount(value: number | null): string {
  return value === null ? '—' : formatAmount(value);
}

function query(): string {
  const params = new URLSearchParams();
  const add = (key: string, value: string) => {
    if (value) {
      params.set(key, value);
    }
  };
  add('from', from.value);
  add('to', to.value);
  add('product', product.value);
  add('distributor', distributor.value);
  add('group', group.value);
  add('delegation', delegation.value);
  add('nature', nature.value);
  const q = params.toString();
  return q ? `?${q}` : '';
}

async function load() {
  error.value = '';
  csv.value = '';
  try {
    data.value = await apiGet<Dashboard>(`/api/ui/v1/reporting/${entity.value}/dashboard${query()}`);
  } catch (e) {
    data.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function exportCsv() {
  csv.value = await apiGetText(`/api/ui/v1/reporting/${entity.value}/export/dashboard${query()}`);
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Tableaux de bord</h1>
      <div class="page__subtitle">
        Commissions par mois, nature et gamme ; reprises ; escomptes versés, apurés et créance ; comptes
        débiteurs ; premiers distributeurs ; anomalies — sur une plage de mois (REP-01, REP-03).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="dash-entity" type="text" v-model="entity" /></label>
        <label>Du mois <input data-testid="dash-from" type="text" v-model="from" placeholder="2026-06-01" /></label>
        <label>Au mois <input data-testid="dash-to" type="text" v-model="to" placeholder="2026-11-01" /></label>
        <label>Produit <input data-testid="dash-product" type="text" v-model="product" /></label>
        <label>Distributeur <input data-testid="dash-distributor" type="text" v-model="distributor" /></label>
        <label>Groupement <input data-testid="dash-group" type="text" v-model="group" /></label>
        <label>Délégation <input data-testid="dash-delegation" type="text" v-model="delegation" /></label>
        <label>Nature <input data-testid="dash-nature-filter" type="text" v-model="nature" /></label>
        <button class="btn" data-testid="dash-refresh" @click="load">Rafraîchir</button>
        <button class="btn sec" data-testid="dash-export" @click="exportCsv">Exporter CSV</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="dash-error" style="margin-top: 10px">{{ error }}</div>
      <pre v-if="csv" data-testid="dash-export-content" style="margin-top: 10px; overflow-x: auto; background: #f6f8fa; padding: 12px">{{ csv }}</pre>
    </section>

    <template v-if="data">
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 14px">
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Reprises</div>
          <div data-testid="dash-reprises" style="font: 500 22px monospace; margin-top: 6px">{{ amount(data.reprises) }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Escomptes versés</div>
          <div data-testid="dash-verses" style="font: 500 22px monospace; margin-top: 6px">{{ amount(data.escomptesVerses) }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Escomptes apurés</div>
          <div data-testid="dash-apures" style="font: 500 22px monospace; margin-top: 6px">{{ amount(data.escomptesApures) }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Créance d'escompte</div>
          <div data-testid="dash-creance" style="font: 500 22px monospace; margin-top: 6px">{{ amount(data.creance) }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Comptes débiteurs</div>
          <div data-testid="dash-debtors" style="font: 500 22px monospace; margin-top: 6px">{{ data.debtorAccounts }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Anomalies</div>
          <div data-testid="dash-anomalies" style="font: 500 22px monospace; margin-top: 6px">{{ data.anomalies }}</div></div>
      </div>

      <section class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Commissions par nature</h2>
        <table>
          <thead><tr><th>Nature</th><th>Montant</th></tr></thead>
          <tbody>
            <tr v-if="data.byNature.length === 0"><td colspan="2" data-testid="no-nature">Aucune commission.</td></tr>
            <tr v-for="(n, i) in data.byNature" :key="i" :data-testid="`dash-nature-${n.label}`">
              <td>{{ n.label }}</td><td class="num">{{ amount(n.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Commissions par gamme</h2>
        <table>
          <thead><tr><th>Gamme</th><th>Montant</th></tr></thead>
          <tbody>
            <tr v-for="(g, i) in data.byGamme" :key="i" :data-testid="`dash-gamme-${i}`">
              <td>{{ g.label }}</td><td class="num">{{ amount(g.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Escomptes par mois</h2>
        <table>
          <thead><tr><th>Mois</th><th>Versés</th><th>Apurés</th><th>Créance</th></tr></thead>
          <tbody>
            <tr v-for="(e, i) in data.escompteByMonth" :key="i" :data-testid="`dash-escompte-${i}`">
              <td>{{ e.month }}</td><td class="num">{{ amount(e.primary) }}</td>
              <td class="num">{{ amount(e.second) }}</td><td class="num">{{ amount(e.third) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Reprises par mois</h2>
        <table>
          <thead><tr><th>Mois</th><th>Montant</th></tr></thead>
          <tbody>
            <tr v-for="(r, i) in data.reprisesByMonth" :key="i" :data-testid="`dash-reprise-${i}`">
              <td>{{ r.month }}</td><td class="num">{{ amount(r.primary) }}</td>
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
              <td>{{ d.label }}</td><td class="num">{{ amount(d.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
