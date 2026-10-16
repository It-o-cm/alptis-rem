<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import { jobCodeLabel, componentLabel, variableStatusLabel } from '../presentation/salesforce';
import HBarChart from '../components/charts/HBarChart.vue';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, EntitySelect, FilterBar, FormField, MonthSelect, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface Result {
  id: number;
  employee: string;
  employeeName: string;
  jobCode: string;
  component: string;
  achievementRate: number;
  amount: number;
  status: string;
  adjustmentReason: string | null;
}

interface PaySummary {
  externalId: string;
  collaborator: string;
  jobCode: string;
  total: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const month = ref('2026-09-01');
const rows = ref<Result[]>([]);
const summary = ref<PaySummary[]>([]);
const error = ref('');
const message = ref('');
const loading = ref(false);
const adjust = ref({ externalId: '', amount: 0, reason: '' });

/** Whether the current user may validate results and record an adjustment. */
const canValidate = (): boolean => session.roles.includes('SALES_MANAGER') || session.roles.includes('BUSINESS_ADMIN');
/** Whether the current user may export the validated variables to pay. */
const canExport = (): boolean => session.roles.includes('FINANCE_CONTROLLER');

/**
 * Formats a monetary value in euros.
 * @param value the amount to format
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Sum of the amounts of all displayed results. */
function total(): number {
  return rows.value.reduce((sum, r) => sum + r.amount, 0);
}

/** Achievement rate per collaborator, feeding the bar chart. */
const achievementData = computed(() => rows.value.map((r) => ({ label: r.employeeName, value: r.achievementRate })));

/** Sum of the per-person pay synthesis. */
function summaryTotal(): number {
  return summary.value.reduce((sum, s) => sum + s.total, 0);
}

/** Columns of the monthly results table. */
const columns: Column<Result>[] = [
  { key: 'employeeName', label: 'Collaborateur' },
  { key: 'jobCode', label: 'Poste', value: (r) => jobCodeLabel(r.jobCode) },
  { key: 'component', label: 'Composante', value: (r) => componentLabel(r.component) },
  { key: 'achievementRate', label: 'Atteinte', align: 'num', value: (r) => `${(r.achievementRate * 100).toFixed(0)} %` },
  { key: 'amount', label: 'Montant', align: 'num', value: (r) => amount(r.amount) },
  { key: 'status', label: 'Statut' },
  { key: 'adjustmentReason', label: 'Motif', value: (r) => r.adjustmentReason ?? '—' },
];

/** Columns of the per-person pay synthesis (FDV-14). */
const summaryColumns: Column<PaySummary>[] = [
  { key: 'externalId', label: 'Matricule' },
  { key: 'collaborator', label: 'Collaborateur' },
  { key: 'jobCode', label: 'Poste', value: (s) => jobCodeLabel(s.jobCode) },
  { key: 'total', label: 'À payer', align: 'num', value: (s) => amount(s.total) },
];

/** Loads the monthly results for the selected entity and month. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<Result[]>(`/api/ui/v1/fdv/${entity.value}/${month.value}/results`);
    summary.value = await apiGet<PaySummary[]>(`/api/ui/v1/fdv/${entity.value}/${month.value}/pay-summary`);
  } catch (e) {
    rows.value = [];
    summary.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/**
 * Validates one computed result.
 * @param r the result row to validate
 */
async function validate(r: Result): Promise<void> {
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/fdv/results/${r.id}/validate`, {});
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Validation refusée';
  }
}

/** Records a motivated adjustment for a collaborator. */
async function submitAdjustment(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/fdv/${entity.value}/${month.value}/adjust`, {
      externalId: adjust.value.externalId,
      amount: adjust.value.amount,
      reason: adjust.value.reason,
    });
    message.value = 'Ajustement enregistré.';
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Ajustement refusé';
  }
}

/** Exports the validated variables of the selected period to pay. */
async function payExport(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ fileName: string; lines: number }>(
      `/api/ui/v1/fdv/${entity.value}/${month.value}/pay-export`, {});
    message.value = `Export paie produit : ${result.fileName} (${result.lines} lignes).`;
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Export refusé';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Validation mensuelle du variable"
      subtitle="Le responsable valide les montants, autorise un ajustement motivé, et la Finance exporte vers la paie une fois tout validé (FDV-13, FDV-14)."
    />

    <FilterBar :loading="loading" :resettable="false" search-label="Rafraîchir" search-testid="fdvv-refresh" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="fdvv-entity" v-model="entity" />
      </FormField>
      <FormField label="Mois" stacked width="date" v-slot="{ id }">
        <MonthSelect :id="id" data-testid="fdvv-month" v-model="month" :entity="entity" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="fdvv-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="fdvv-message">{{ message }}</AppMessage>

    <div v-if="canExport()">
      <AppButton variant="secondary" data-testid="fdvv-export" @click="payExport">Export paie</AppButton>
    </div>

    <AppCard v-if="canValidate()" title="Ajustement motivé" data-testid="fdvv-adjust-form">
      <AppForm @submit="submitAdjustment">
        <FormField label="Collaborateur" width="short" v-slot="{ id }">
          <input :id="id" data-testid="fdvv-adj-id" type="text" v-model="adjust.externalId" />
        </FormField>
        <FormField label="Montant" width="amount" v-slot="{ id }">
          <input :id="id" data-testid="fdvv-adj-amount" type="number" v-model.number="adjust.amount" />
        </FormField>
        <FormField label="Motif" width="long" v-slot="{ id }">
          <input :id="id" data-testid="fdvv-adj-reason" type="text" v-model="adjust.reason" />
        </FormField>
        <AppButton variant="secondary" type="submit" data-testid="fdvv-adj-submit">Enregistrer</AppButton>
      </AppForm>
    </AppCard>

    <AppCard v-if="rows.length > 0">
      <HBarChart :data="achievementData" title="Atteinte de l'objectif par collaborateur" format="percent"
                 testid="chart-fdvv" />
    </AppCard>

    <AppCard>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        row-key="id"
        empty="Aucun résultat."
        empty-testid="no-result"
        :row-testid="(r) => `result-row-${r.id}`"
      >
        <template #cell-amount="{ row }"><span :data-testid="`result-amount-${row.component}`">{{ amount(row.amount) }}</span></template>
        <template #cell-status="{ row }"><span :data-testid="`result-status-${row.id}`">{{ variableStatusLabel(row.status) }}</span></template>
        <template #actions="{ row }">
          <AppButton
            v-if="canValidate() && row.status === 'CALCULATED'"
            variant="secondary"
            :data-testid="`result-validate-${row.id}`"
            @click="validate(row)"
          >
            Valider
          </AppButton>
        </template>
      </DataTable>
      <p class="fdvv-total">Total à payer <span class="mono" data-testid="fdvv-total">{{ amount(total()) }}</span></p>
    </AppCard>

    <AppCard title="Synthèse des variables à payer par personne (FDV-14)" data-testid="fdvv-summary">
      <DataTable
        :columns="summaryColumns"
        :rows="summary"
        :loading="loading"
        row-key="externalId"
        empty="Aucune variable à payer."
        empty-testid="no-pay-summary"
        :row-testid="(s) => `pay-summary-${s.externalId}`"
      >
        <template #cell-externalId="{ row }"><span class="mono">{{ row.externalId }}</span></template>
      </DataTable>
      <p class="fdvv-total">
        Total synthèse <span class="mono" data-testid="fdvv-summary-total">{{ amount(summaryTotal()) }}</span>
      </p>
    </AppCard>
  </div>
</template>

<style scoped>
.fdvv-total {
  margin: 0;
  text-align: right;
  font-weight: 600;
}
</style>
