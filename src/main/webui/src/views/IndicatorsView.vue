<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';

interface Indicators {
  entity: string;
  activeRules: number;
  manualShare: number;
  clawback: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const data = ref<Indicators | null>(null);
const error = ref('');

function percent(value: number): string {
  return (value * 100).toFixed(2).replace('.', ',') + ' %';
}

function amount(value: number): string {
  return formatAmount(value);
}

async function load() {
  error.value = '';
  try {
    data.value = await apiGet<Indicators>(`/api/ui/v1/reporting/${entity.value}/indicators`);
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
      <h1 class="page__title" data-testid="page-title">Indicateurs de fonctionnement</h1>
      <div class="page__subtitle">
        Règles actives, part des lignes ajustées à la main et montants repris de l'entité (REP-09).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="ind-entity" type="text" v-model="entity" /></label>
        <button class="btn" data-testid="ind-refresh" @click="load">Rafraîchir</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="ind-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <div v-if="data" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px">
      <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Règles actives</div>
        <div data-testid="ind-rules" style="font: 500 22px monospace; margin-top: 6px">{{ data.activeRules }}</div></div>
      <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Lignes ajustées à la main</div>
        <div data-testid="ind-manual" style="font: 500 22px monospace; margin-top: 6px">{{ percent(data.manualShare) }}</div></div>
      <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Montants repris</div>
        <div data-testid="ind-clawback" style="font: 500 22px monospace; margin-top: 6px">{{ amount(data.clawback) }}</div></div>
    </div>
  </div>
</template>
