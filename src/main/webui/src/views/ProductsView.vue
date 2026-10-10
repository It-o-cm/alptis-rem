<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface ProductView {
  entityCode: string;
  code: string;
  label: string;
  rangeCode: string | null;
  insurerCode: string | null;
  commissionable: boolean;
}

const rows = ref<ProductView[]>([]);
const error = ref('');
const loading = ref(false);

/** Columns of the products list. */
const columns: Column<ProductView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'rangeCode', label: 'Gamme', value: (p) => p.rangeCode ?? '—' },
  { key: 'insurerCode', label: 'Assureur', value: (p) => p.insurerCode ?? '—' },
  { key: 'commissionable', label: 'Commissionnable' },
];

/** Loads the products of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<ProductView[]>('/api/ui/v1/products');
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
    <PageHeading title="Produits" subtitle="Gammes, produits et options par entité (REF-01)." />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        :row-key="(p) => p.entityCode + p.code"
        empty="Aucun produit."
        empty-testid="empty"
        :row-testid="(p) => `product-row-${p.code}`"
      >
        <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
        <template #cell-commissionable="{ row }">
          <span :class="row.commissionable ? 'tag ok' : 'tag mute'">{{ row.commissionable ? 'Oui' : 'Non' }}</span>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>
