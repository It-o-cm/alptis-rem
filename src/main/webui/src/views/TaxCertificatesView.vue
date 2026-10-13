<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import { AppCard, AppMessage, DataTable, EntitySelect, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface CertificateView {
  beneficiary: string;
  year: number;
  total: number;
  lineCount: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const year = ref('');
const rows = ref<CertificateView[]>([]);
const error = ref('');
const loading = ref(false);

/**
 * Formats an amount in euros.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Columns of the tax certificates table. */
const columns: Column<CertificateView>[] = [
  { key: 'beneficiary', label: 'Bénéficiaire' },
  { key: 'year', label: 'Exercice', align: 'num' },
  { key: 'total', label: 'Commissions payées', align: 'num', value: (c) => amount(c.total) },
  { key: 'lineCount', label: 'Lignes', align: 'num' },
];

/** Loads the annual tax certificates of the entity, for the chosen or current year. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    const q = year.value === '' ? '' : `?year=${encodeURIComponent(year.value)}`;
    rows.value = await apiGet<CertificateView[]>(`/api/ui/v1/documents/tax-certificates/${entity.value}${q}`);
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
      title="Attestations fiscales annuelles"
      subtitle="Le total des commissions payées à chaque bénéficiaire pour l'exercice (BRD-08)."
    />

    <FilterBar :loading="loading" search-label="Rafraîchir" search-testid="tax-refresh" :resettable="false" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="tax-entity" v-model="entity" />
      </FormField>
      <FormField label="Exercice" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="tax-year" type="text" v-model="year" placeholder="année courante" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="tax-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucune commission payée pour l'exercice."
        empty-testid="tax-empty"
        :row-testid="(c) => `tax-row-${c.beneficiary}`"
      />
    </AppCard>
  </div>
</template>
