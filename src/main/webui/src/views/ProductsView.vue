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

interface InsurerView { code: string; name: string; role: string | null; }
interface RangeView { code: string; label: string; target: string | null; }
interface OptionView { productCode: string; code: string; label: string; optionType: string | null; }

const products = ref<ProductView[]>([]);
const insurers = ref<InsurerView[]>([]);
const ranges = ref<RangeView[]>([]);
const options = ref<OptionView[]>([]);
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

/** Columns of the insurers list. */
const insurerColumns: Column<InsurerView>[] = [
  { key: 'code', label: 'Code' },
  { key: 'name', label: 'Nom' },
  { key: 'role', label: 'Rôle', value: (i) => i.role ?? '—' },
];

/** Columns of the ranges list. */
const rangeColumns: Column<RangeView>[] = [
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'target', label: 'Cible', value: (r) => r.target ?? '—' },
];

/** Columns of the options list. */
const optionColumns: Column<OptionView>[] = [
  { key: 'productCode', label: 'Produit' },
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'optionType', label: 'Type', value: (o) => o.optionType ?? '—' },
];

/** Loads the insurers, ranges, products and options of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    insurers.value = await apiGet<InsurerView[]>('/api/ui/v1/insurers');
    ranges.value = await apiGet<RangeView[]>('/api/ui/v1/ranges');
    products.value = await apiGet<ProductView[]>('/api/ui/v1/products');
    options.value = await apiGet<OptionView[]>('/api/ui/v1/options');
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
    <PageHeading title="Assureurs, gammes, produits et options" subtitle="Le référentiel produit par entité (REF-01)." />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>

    <AppCard title="Assureurs" data-testid="insurers">
      <DataTable
        :columns="insurerColumns"
        :rows="insurers"
        empty="Aucun assureur."
        empty-testid="no-insurer"
        :row-testid="(i) => `insurer-row-${i.code}`"
      />
    </AppCard>

    <AppCard title="Gammes" data-testid="ranges">
      <DataTable
        :columns="rangeColumns"
        :rows="ranges"
        empty="Aucune gamme."
        empty-testid="no-range"
        :row-testid="(r) => `range-row-${r.code}`"
      />
    </AppCard>

    <AppCard title="Produits" flush>
      <DataTable
        :columns="columns"
        :rows="products"
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

    <AppCard title="Options" data-testid="options">
      <DataTable
        :columns="optionColumns"
        :rows="options"
        empty="Aucune option."
        empty-testid="no-option"
        :row-testid="(o) => `option-row-${o.code}`"
      />
    </AppCard>
  </div>
</template>
