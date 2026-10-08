<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { formatShareList, type Share as ShareView } from '../presentation/payeeShares';

interface PayeeRuleView {
  entityCode: string;
  productionCode: string | null;
  partnerCode: string | null;
  rangeCode: string | null;
  frequency: string | null;
  validFrom: string | null;
  validTo: string | null;
  shares: ShareView[];
}

const rows = ref<PayeeRuleView[]>([]);
const error = ref('');

async function load() {
  error.value = '';
  try {
    rows.value = await apiGet<PayeeRuleView[]>('/api/ui/v1/payee-rules');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Règles de versement</h1>
      <div class="page__subtitle">Bénéficiaire, parts et périodicité par périmètre (REF-06).</div>
    </div>
    <div v-if="error" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">{{ error }}</div>
    <section v-else class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Entité</th><th>Code prod.</th><th>Distributeur</th><th>Gamme</th><th>Périodicité</th><th>Du</th><th>Au</th><th>Parts</th></tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="8" data-testid="empty">Aucune règle de versement.</td></tr>
          <tr v-for="(r, i) in rows" :key="i" :data-testid="`rule-row-${i}`">
            <td>{{ r.entityCode }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ r.productionCode ?? '—' }}</td>
            <td>{{ r.partnerCode ?? '—' }}</td>
            <td>{{ r.rangeCode ?? '—' }}</td>
            <td>{{ r.frequency ?? '—' }}</td>
            <td>{{ r.validFrom ?? '—' }}</td>
            <td>{{ r.validTo ?? '—' }}</td>
            <td data-testid="rule-shares">{{ formatShareList(r.shares) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
