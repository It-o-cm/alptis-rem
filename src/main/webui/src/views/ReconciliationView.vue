<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import { AppCard, AppMessage, DataTable, DetailList, DetailRow, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface BeneficiaryRow {
  distributor: string;
  acquired: number;
  paid: number;
  gap: number;
}

interface Reconciliation {
  acquired: number;
  paid: number;
  gap: number;
  beneficiaries: BeneficiaryRow[];
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const data = ref<Reconciliation | null>(null);
const error = ref('');
const loading = ref(false);

/**
 * Formats an amount in euros.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Columns of the per-beneficiary reconciliation table. */
const columns: Column<BeneficiaryRow>[] = [
  { key: 'distributor', label: 'Bénéficiaire' },
  { key: 'acquired', label: 'Acquises', align: 'num', value: (b) => amount(b.acquired) },
  { key: 'paid', label: 'Payées', align: 'num', value: (b) => amount(b.paid) },
  { key: 'gap', label: 'Reste à payer', align: 'num', value: (b) => amount(b.gap) },
];

/** Loads the reconciliation of the current entity. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    data.value = await apiGet<Reconciliation>(`/api/ui/v1/accounting/${entity.value}/reconciliation`);
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
      title="Rapprochement des commissions acquises et payées"
      subtitle="Les commissions acquises comparées aux commissions payées, par bénéficiaire et au global (FIN-06)."
    />

    <FilterBar :loading="loading" search-label="Rafraîchir" search-testid="rec-refresh" :resettable="false" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="rec-entity" type="text" v-model="entity" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="rec-error">{{ error }}</AppMessage>

    <AppCard v-if="data" data-testid="rec-global">
      <h2 class="rec-title">Global</h2>
      <DetailList>
        <DetailRow label="Acquises"><span data-testid="rec-acquired">{{ amount(data.acquired) }}</span></DetailRow>
        <DetailRow label="Payées"><span data-testid="rec-paid">{{ amount(data.paid) }}</span></DetailRow>
        <DetailRow label="Reste à payer"><span data-testid="rec-gap">{{ amount(data.gap) }}</span></DetailRow>
      </DetailList>
    </AppCard>

    <AppCard flush title="Par bénéficiaire">
      <DataTable
        :columns="columns"
        :rows="data ? data.beneficiaries : []"
        :loading="loading"
        empty="Aucune commission acquise."
        empty-testid="rec-empty"
        :row-testid="(b) => `rec-row-${b.distributor}`"
      />
    </AppCard>
  </div>
</template>

<style scoped>
.rec-title {
  margin: 0;
  font-size: var(--fs-h2);
  font-weight: 600;
}
</style>
