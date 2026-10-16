<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { formatPercent } from '../presentation/format';
import { indicatorSourceLabel } from '../presentation/referentials';
import { AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface QualityIndicatorView {
  entityCode: string;
  partnerCode: string | null;
  indicatorType: string | null;
  rangeCode: string | null;
  periodTo: string | null;
  value: string | null;
  source: string | null;
}

const rows = ref<QualityIndicatorView[]>([]);
const error = ref('');
const loading = ref(false);

/** Columns of the quality-indicators list. */
const columns: Column<QualityIndicatorView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'partnerCode', label: 'Distributeur', value: (q) => q.partnerCode ?? '—' },
  { key: 'indicatorType', label: 'Indicateur', value: (q) => q.indicatorType ?? '—' },
  { key: 'rangeCode', label: 'Gamme', value: (q) => q.rangeCode ?? 'Global' },
  { key: 'periodTo', label: 'Période (fin)', value: (q) => q.periodTo ?? '—' },
  { key: 'value', label: 'Valeur', align: 'num', value: (q) => (q.value ? formatPercent(Number(q.value)) : '—') },
  { key: 'source', label: 'Origine', value: (q) => indicatorSourceLabel(q.source) },
];

/** Loads the quality indicators of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<QualityIndicatorView[]>('/api/ui/v1/quality-indicators');
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
      title="Indicateurs qualité"
      subtitle="Indicateurs de qualité par distributeur et par période (REF-07)."
    />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucun indicateur."
        empty-testid="empty"
        :row-testid="() => 'indicator-row'"
      >
        <template #cell-partnerCode="{ row }"><span class="mono">{{ row.partnerCode ?? '—' }}</span></template>
        <template #cell-indicatorType="{ row }">
          <span data-testid="indicator-type">{{ row.indicatorType ?? '—' }}</span>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>
