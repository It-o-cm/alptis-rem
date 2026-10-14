<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';
import { transferStatusLabel } from '../presentation/transfers';

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
const loading = ref(false);

/** Columns of the portfolio-transfers table. */
const transferColumns: Column<TransferView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'fromPartnerCode', label: 'Cédant', value: (t) => t.fromPartnerCode ?? '—' },
  { key: 'toPartnerCode', label: 'Repreneur', value: (t) => t.toPartnerCode ?? '—' },
  { key: 'effectDate', label: 'Effet', value: (t) => t.effectDate ?? '—' },
  { key: 'advanceDebtHandling', label: "Créance d'escompte", value: (t) => t.advanceDebtHandling ?? '—' },
  { key: 'status', label: 'Statut' },
];

/** Columns of the replacement-orders table. */
const orderColumns: Column<ReplacementOrderView>[] = [
  { key: 'contractNumber', label: 'Adhésion', value: (o) => o.contractNumber ?? '—' },
  { key: 'fromProductionCode', label: 'Ancien code', value: (o) => o.fromProductionCode ?? '—' },
  { key: 'toProductionCode', label: 'Nouveau code', value: (o) => o.toProductionCode ?? '—' },
  { key: 'receivedDate', label: 'Reçu le', value: (o) => o.receivedDate ?? '—' },
  { key: 'effectDate', label: 'Effet', value: (o) => o.effectDate ?? '—' },
];

/** Loads the portfolio transfers and replacement orders of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    transfers.value = await apiGet<TransferView[]>('/api/ui/v1/portfolio-transfers');
    orders.value = await apiGet<ReplacementOrderView[]>('/api/ui/v1/replacement-orders');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Cessions et ordres de remplacement"
      subtitle="Transferts de portefeuille et ordres de remplacement reçus du CRM (REF-09)."
    />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>

    <AppCard v-if="!error" title="Cessions de portefeuille">
      <DataTable
        :columns="transferColumns"
        :rows="transfers"
        :loading="loading"
        empty="Aucune cession."
        empty-testid="no-transfers"
        :row-testid="() => 'transfer-row'"
      >
        <template #cell-fromPartnerCode="{ row }"><span class="mono">{{ row.fromPartnerCode ?? '—' }}</span></template>
        <template #cell-toPartnerCode="{ row }"><span class="mono">{{ row.toPartnerCode ?? '—' }}</span></template>
        <template #cell-status="{ row }"><span class="tag info">{{ transferStatusLabel(row.status) }}</span></template>
      </DataTable>
    </AppCard>

    <AppCard v-if="!error" title="Ordres de remplacement">
      <DataTable
        :columns="orderColumns"
        :rows="orders"
        :loading="loading"
        empty="Aucun ordre de remplacement."
        empty-testid="no-orders"
        :row-testid="() => 'order-row'"
      >
        <template #cell-contractNumber="{ row }"><span class="mono">{{ row.contractNumber ?? '—' }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>
