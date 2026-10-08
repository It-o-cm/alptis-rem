<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { formatPercent } from '../presentation/format';

interface QualityIndicatorView {
  entityCode: string;
  partnerCode: string | null;
  indicatorType: string | null;
  rangeCode: string | null;
  periodTo: string | null;
  value: string | null;
}

const rows = ref<QualityIndicatorView[]>([]);
const error = ref('');

async function load() {
  error.value = '';
  try {
    rows.value = await apiGet<QualityIndicatorView[]>('/api/ui/v1/quality-indicators');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Indicateurs qualité</h1>
      <div class="page__subtitle">Indicateurs de qualité par distributeur et par période (REF-07).</div>
    </div>
    <div v-if="error" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">{{ error }}</div>
    <section v-else class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Entité</th><th>Distributeur</th><th>Indicateur</th><th>Gamme</th><th>Période (fin)</th><th class="num">Valeur</th></tr></thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="6" data-testid="empty">Aucun indicateur.</td></tr>
          <tr v-for="(q, i) in rows" :key="i" data-testid="indicator-row">
            <td>{{ q.entityCode }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ q.partnerCode ?? '—' }}</td>
            <td data-testid="indicator-type">{{ q.indicatorType ?? '—' }}</td>
            <td>{{ q.rangeCode ?? 'Global' }}</td>
            <td>{{ q.periodTo ?? '—' }}</td>
            <td class="num">{{ q.value ? formatPercent(Number(q.value)) : '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
