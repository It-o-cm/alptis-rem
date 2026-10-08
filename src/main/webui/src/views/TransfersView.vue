<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';

interface TransferView {
  entityCode: string;
  fromPartnerCode: string | null;
  toPartnerCode: string | null;
  effectDate: string | null;
  advanceDebtHandling: string | null;
  status: string | null;
}

interface ReplacementOrderView {
  contractNumber: string | null;
  fromProductionCode: string | null;
  toProductionCode: string | null;
  receivedDate: string | null;
  effectDate: string | null;
}

const transfers = ref<TransferView[]>([]);
const orders = ref<ReplacementOrderView[]>([]);
const error = ref('');

async function load() {
  error.value = '';
  try {
    transfers.value = await apiGet<TransferView[]>('/api/ui/v1/portfolio-transfers');
    orders.value = await apiGet<ReplacementOrderView[]>('/api/ui/v1/replacement-orders');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Cessions et ordres de remplacement</h1>
      <div class="page__subtitle">Transferts de portefeuille et ordres de remplacement reçus du CRM (REF-09).</div>
    </div>
    <div v-if="error" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">{{ error }}</div>

    <section v-else class="card" style="overflow-x: auto">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Cessions de portefeuille</h2>
      <table>
        <thead><tr><th>Entité</th><th>Cédant</th><th>Repreneur</th><th>Effet</th><th>Créance d'escompte</th><th>Statut</th></tr></thead>
        <tbody>
          <tr v-if="transfers.length === 0"><td colspan="6" data-testid="no-transfers">Aucune cession.</td></tr>
          <tr v-for="(t, i) in transfers" :key="i" data-testid="transfer-row">
            <td>{{ t.entityCode }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ t.fromPartnerCode ?? '—' }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ t.toPartnerCode ?? '—' }}</td>
            <td>{{ t.effectDate ?? '—' }}</td>
            <td>{{ t.advanceDebtHandling ?? '—' }}</td>
            <td><span class="tag info">{{ t.status ?? '—' }}</span></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="!error" class="card" style="overflow-x: auto">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Ordres de remplacement</h2>
      <table>
        <thead><tr><th>Adhésion</th><th>Ancien code</th><th>Nouveau code</th><th>Reçu le</th><th>Effet</th></tr></thead>
        <tbody>
          <tr v-if="orders.length === 0"><td colspan="5" data-testid="no-orders">Aucun ordre de remplacement.</td></tr>
          <tr v-for="(o, i) in orders" :key="i" data-testid="order-row">
            <td style="font-family: 'IBM Plex Mono', monospace">{{ o.contractNumber ?? '—' }}</td>
            <td>{{ o.fromProductionCode ?? '—' }}</td>
            <td>{{ o.toProductionCode ?? '—' }}</td>
            <td>{{ o.receivedDate ?? '—' }}</td>
            <td>{{ o.effectDate ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
