<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';

interface DistributorView {
  partnerCode: string;
  legalName: string;
  distributorType: string | null;
  groupCode: string | null;
}

interface StatusView {
  entityCode: string;
  status: string;
  validFrom: string | null;
  validTo: string | null;
}

interface ProductionCodeView {
  entityCode: string;
  code: string;
  label: string | null;
  status: string | null;
}

interface DistributorDetail {
  partnerCode: string;
  legalName: string;
  groupCode: string | null;
  statuses: StatusView[];
  productionCodes: ProductionCodeView[];
}

const term = ref('');
const results = ref<DistributorView[]>([]);
const detail = ref<DistributorDetail | null>(null);
const error = ref('');

async function search() {
  error.value = '';
  detail.value = null;
  try {
    const page = await apiGet<{ items: DistributorView[] }>(
      `/api/ui/v1/distributors?q=${encodeURIComponent(term.value)}`,
    );
    results.value = page.items;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de recherche';
  }
}

async function open(distributor: DistributorView) {
  detail.value = await apiGet<DistributorDetail>(`/api/ui/v1/distributors/${distributor.partnerCode}`);
}

onMounted(search);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Recherche distributeurs</h1>
      <div class="page__subtitle">Par code partenaire ou raison sociale, dans vos entités.</div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 10px; align-items: end">
        <label style="flex: 1">Recherche
          <input data-testid="search-term" type="text" v-model="term" style="width: 100%" @keyup.enter="search" />
        </label>
        <button class="btn" data-testid="search-button" @click="search">Rechercher</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="search-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Code partenaire</th><th>Raison sociale</th><th>Type</th><th>Groupement</th></tr></thead>
        <tbody>
          <tr v-if="results.length === 0"><td colspan="4" data-testid="empty">Aucun distributeur.</td></tr>
          <tr v-for="d in results" :key="d.partnerCode" :data-testid="`distributor-row-${d.partnerCode}`"
              style="cursor: pointer" @click="open(d)">
            <td style="font-family: 'IBM Plex Mono', monospace">{{ d.partnerCode }}</td>
            <td>{{ d.legalName }}</td>
            <td>{{ d.distributorType ?? '—' }}</td>
            <td>{{ d.groupCode ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="detail" class="card" style="padding: 18px 20px" data-testid="distributor-detail">
      <h2 style="margin: 0 0 6px; font-size: 16px">{{ detail.legalName }} · {{ detail.partnerCode }}</h2>
      <div style="font-size: 14px; color: #4A5868; margin-bottom: 12px">
        Groupement : {{ detail.groupCode ?? '—' }}
      </div>

      <h3 style="font-size: 14px; margin: 8px 0">Statut par entité</h3>
      <table>
        <thead><tr><th>Entité</th><th>Statut</th><th>Depuis</th><th>Jusqu'à</th></tr></thead>
        <tbody>
          <tr v-for="(s, i) in detail.statuses" :key="i" data-testid="status-row">
            <td>{{ s.entityCode }}</td><td>{{ s.status }}</td><td>{{ s.validFrom ?? '—' }}</td><td>{{ s.validTo ?? '—' }}</td>
          </tr>
        </tbody>
      </table>

      <h3 style="font-size: 14px; margin: 14px 0 8px">Codes de production</h3>
      <table>
        <thead><tr><th>Entité</th><th>Code</th><th>Libellé</th><th>Statut</th></tr></thead>
        <tbody>
          <tr v-if="detail.productionCodes.length === 0"><td colspan="4" data-testid="no-codes">Aucun code de production.</td></tr>
          <tr v-for="(c, i) in detail.productionCodes" :key="i" :data-testid="`code-row-${c.code}`">
            <td>{{ c.entityCode }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ c.code }}</td>
            <td>{{ c.label ?? '—' }}</td><td>{{ c.status ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
