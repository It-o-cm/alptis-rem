<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';

interface CoverageView {
  coverageCode: string;
  status: string | null;
  annualPremium: string | null;
}

interface ContractView {
  entityCode: string;
  contractNumber: string;
  sourceSystem: string;
  status: string | null;
  coverages: CoverageView[];
}

const term = ref('');
const results = ref<ContractView[]>([]);
const error = ref('');

async function search() {
  error.value = '';
  try {
    const page = await apiGet<{ items: ContractView[] }>(`/api/ui/v1/contracts?q=${encodeURIComponent(term.value)}`);
    results.value = page.items;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de recherche';
  }
}

onMounted(search);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Recherche contrats</h1>
      <div class="page__subtitle">Adhésions et garanties, dans vos entités.</div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 10px; align-items: end">
        <label style="flex: 1">Numéro d'adhésion
          <input data-testid="search-term" type="text" v-model="term" style="width: 100%" @keyup.enter="search" />
        </label>
        <button class="btn" data-testid="search-button" @click="search">Rechercher</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="search-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Entité</th><th>Adhésion</th><th>Source</th><th>Statut</th><th>Garanties</th></tr></thead>
        <tbody>
          <tr v-if="results.length === 0"><td colspan="5" data-testid="empty">Aucune adhésion.</td></tr>
          <tr v-for="c in results" :key="c.entityCode + c.sourceSystem + c.contractNumber"
              :data-testid="`contract-row-${c.contractNumber}`">
            <td>{{ c.entityCode }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ c.contractNumber }}</td>
            <td>{{ c.sourceSystem }}</td>
            <td>{{ c.status ?? '—' }}</td>
            <td>
              <span v-for="cov in c.coverages" :key="cov.coverageCode" class="tag info" style="margin-right: 4px">
                {{ cov.coverageCode }}<span v-if="cov.annualPremium"> · {{ cov.annualPremium }}</span>
              </span>
              <span v-if="c.coverages.length === 0">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
