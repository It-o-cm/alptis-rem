<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';

interface SearchRow {
  distributor: string;
  product: string;
  gamme: string;
  nature: string;
  basis: number | null;
  rate: number | null;
  amount: number;
  rule: string;
  statementNumber: string;
  month: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const filters = ref({ product: '', distributor: '', group: '', delegation: '', nature: '', month: '' });
const rows = ref<SearchRow[]>([]);
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
  add('product', filters.value.product);
  add('distributor', filters.value.distributor);
  add('group', filters.value.group);
  add('delegation', filters.value.delegation);
  add('nature', filters.value.nature);
  add('month', filters.value.month);
  const q = params.toString();
  return q ? `?${q}` : '';
}

async function search() {
  error.value = '';
  csv.value = '';
  try {
    rows.value = await apiGet<SearchRow[]>(`/api/ui/v1/reporting/${entity.value}/search${query()}`);
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Recherche impossible';
  }
}

async function exportCsv() {
  csv.value = await apiGetText(`/api/ui/v1/reporting/${entity.value}/export/search${query()}`);
}

onMounted(search);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Recherche de lignes de commission</h1>
      <div class="page__subtitle">
        Combien a été payé, à qui, quand et pourquoi : la ligne, sa gamme, son assiette, son taux, sa règle
        et son bordereau (REP-02, REP-03, FIN-07).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="cs-entity" type="text" v-model="entity" /></label>
        <label>Produit <input data-testid="cs-product" type="text" v-model="filters.product" /></label>
        <label>Distributeur <input data-testid="cs-distributor" type="text" v-model="filters.distributor" /></label>
        <label>Groupement <input data-testid="cs-group" type="text" v-model="filters.group" /></label>
        <label>Délégation <input data-testid="cs-delegation" type="text" v-model="filters.delegation" /></label>
        <label>Nature <input data-testid="cs-nature-filter" type="text" v-model="filters.nature" /></label>
        <label>Mois <input data-testid="cs-month" type="text" v-model="filters.month" /></label>
        <button class="btn" data-testid="cs-search" @click="search">Rechercher</button>
        <button class="btn sec" data-testid="cs-export" @click="exportCsv">Exporter CSV</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="cs-error" style="margin-top: 10px">{{ error }}</div>
      <pre v-if="csv" data-testid="cs-export-content" style="margin-top: 10px; overflow-x: auto; background: #f6f8fa; padding: 12px">{{ csv }}</pre>
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Distributeur</th><th>Produit</th><th>Gamme</th><th>Nature</th><th>Assiette</th><th>Taux</th><th>Montant</th><th>Règle</th><th>Bordereau</th><th>Mois</th></tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="10" data-testid="no-row">Aucune ligne.</td></tr>
          <tr v-for="(r, i) in rows" :key="i" :data-testid="`cs-row-${i}`">
            <td>{{ r.distributor }}</td>
            <td>{{ r.product }}</td>
            <td>{{ r.gamme }}</td>
            <td :data-testid="`cs-nature-${i}`">{{ r.nature }}</td>
            <td class="num">{{ amount(r.basis) }}</td>
            <td class="num">{{ r.rate ?? '—' }}</td>
            <td class="num">{{ amount(r.amount) }}</td>
            <td>{{ r.rule }}</td>
            <td>{{ r.statementNumber }}</td>
            <td>{{ r.month ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
