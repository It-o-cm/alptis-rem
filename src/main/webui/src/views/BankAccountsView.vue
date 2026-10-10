<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface BankAccountView {
  entityCode: string;
  holderType: string | null;
  holderName: string;
  iban: string;
  bic: string | null;
  validFrom: string | null;
  validTo: string | null;
}

const rows = ref<BankAccountView[]>([]);
const error = ref('');
const loading = ref(false);

/** Columns of the bank-accounts list. */
const columns: Column<BankAccountView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'holderName', label: 'Titulaire' },
  { key: 'holderType', label: 'Type', value: (b) => b.holderType ?? '—' },
  { key: 'iban', label: 'IBAN' },
  { key: 'bic', label: 'BIC', value: (b) => b.bic ?? '—' },
  { key: 'validFrom', label: 'Depuis', value: (b) => b.validFrom ?? '—' },
];

/** Loads the bank accounts of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<BankAccountView[]>('/api/ui/v1/bank-accounts');
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
      title="Coordonnées bancaires"
      subtitle="IBAN (masqués) reçus des outils de gestion ; un changement est signalé avant le paiement suivant (REF-05)."
    />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucune coordonnée bancaire."
        empty-testid="empty"
        :row-testid="() => 'bank-row'"
      >
        <template #cell-iban="{ row }"><span class="mono" data-testid="bank-iban">{{ row.iban }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>
