<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';

interface SearchRow {
  distributor: string;
  product: string;
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
const filters = ref({ product: '', distributor: '', nature: '', month: '' });
const rows = ref<SearchRow[]>([]);
const error = ref('');

function amount(value: number | null): string {
  return value === null ? '—' : formatAmount(value);
}

async function search() {
  error.value = '';
  const params = new URLSearchParams();
  if (filters.value.product) params.set('product', filters.value.product);
  if (filters.value.distributor) params.set('distributor', filters.value.distributor);
  if (filters.value.nature) params.set('nature', filters.value.nature);
  if (filters.value.month) params.set('month', filters.value.month);
  const q = params.toString();
  try {
    rows.value = await apiGet<SearchRow[]>(`/api/ui/v1/reporting/${entity.value}/search${q ? '?' + q : ''}`);
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Recherche impossible';
  }
}

onMounted(search);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Recherche de lignes de commission</h1>
      <div class="page__subtitle">
        Combien a été payé, à qui, quand et pourquoi : la ligne, son assiette, son taux, sa règle et son
        bordereau (REP-02, REP-03, FIN-07).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="cs-entity" type="text" v-model="entity" /></label>
        <label>Produit <input data-testid="cs-product" type="text" v-model="filters.product" /></label>
        <label>Distributeur <input data-testid="cs-distributor" type="text" v-model="filters.distributor" /></label>
        <label>Nature <input data-testid="cs-nature" type="text" v-model="filters.nature" /></label>
        <label>Mois <input data-testid="cs-month" type="text" v-model="filters.month" /></label>
        <button class="btn" data-testid="cs-search" @click="search">Rechercher</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="cs-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Distributeur</th><th>Produit</th><th>Nature</th><th>Assiette</th><th>Taux</th><th>Montant</th><th>Règle</th><th>Bordereau</th><th>Mois</th></tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="9" data-testid="no-row">Aucune ligne.</td></tr>
          <tr v-for="(r, i) in rows" :key="i" :data-testid="`cs-row-${i}`">
            <td>{{ r.distributor }}</td>
            <td>{{ r.product }}</td>
            <td :data-testid="`cs-nature-${i}`">{{ r.nature }}</td>
            <td>{{ amount(r.basis) }}</td>
            <td>{{ r.rate ?? '—' }}</td>
            <td>{{ amount(r.amount) }}</td>
            <td>{{ r.rule }}</td>
            <td>{{ r.statementNumber }}</td>
            <td>{{ r.month ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
