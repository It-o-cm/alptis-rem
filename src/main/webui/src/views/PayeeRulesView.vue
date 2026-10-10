<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { formatShareList, type Share as ShareView } from '../presentation/payeeShares';
import { AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

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
const loading = ref(false);

/** Columns of the payee-rules list. */
const columns: Column<PayeeRuleView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'productionCode', label: 'Code prod.', value: (r) => r.productionCode ?? '—' },
  { key: 'partnerCode', label: 'Distributeur', value: (r) => r.partnerCode ?? '—' },
  { key: 'rangeCode', label: 'Gamme', value: (r) => r.rangeCode ?? '—' },
  { key: 'frequency', label: 'Périodicité', value: (r) => r.frequency ?? '—' },
  { key: 'validFrom', label: 'Du', value: (r) => r.validFrom ?? '—' },
  { key: 'validTo', label: 'Au', value: (r) => r.validTo ?? '—' },
  { key: 'shares', label: 'Parts', value: (r) => formatShareList(r.shares) },
];

/** Loads the payee rules of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<PayeeRuleView[]>('/api/ui/v1/payee-rules');
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
    <PageHeading title="Règles de versement" subtitle="Bénéficiaire, parts et périodicité par périmètre (REF-06)." />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucune règle de versement."
        empty-testid="empty"
        :row-testid="(r) => `rule-row-${rows.indexOf(r)}`"
      >
        <template #cell-productionCode="{ row }"><span class="mono">{{ row.productionCode ?? '—' }}</span></template>
        <template #cell-shares="{ row }"><span data-testid="rule-shares">{{ formatShareList(row.shares) }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>
