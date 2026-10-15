<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canApproveAdjustment, canOperateCycle } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';
import { adjustmentStatusLabel, adjustmentTypeLabel, ADJUSTMENT_TYPE_OPTIONS } from '../presentation/lines';
import { AppButton, AppCard, AppForm, AppMessage, CodeSelect, DataTable, EntitySelect, FilterBar, FormField, MonthSelect, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface AdjustmentView {
  id: number;
  type: string;
  amount: number;
  reason: string;
  attachmentRef: string | null;
  correctedLineId: number | null;
  status: string;
  submitter: string;
  approver: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 ? session.entities[0] : '');
const month = ref('2026-09-01');
const rows = ref<AdjustmentView[]>([]);
const error = ref('');
const message = ref('');
const loading = ref(false);
const form = ref({
  type: 'CORRECTION', amount: 0, payeeId: 0, reason: '', attachmentRef: '', correctedLineId: null as number | null,
  counterpartPayeeId: null as number | null,
  autoReverse: false, repaymentSchedule: '',
});

/** Whether the current user may operate the cycle (submit an adjustment). */
const canOperate = (): boolean => canOperateCycle(session.roles);

/** Whether the current user may approve an adjustment (includes the business validator, REG-03). */
const canApprove = (): boolean => canApproveAdjustment(session.roles);

/** Columns of the adjustments table. */
const columns: Column<AdjustmentView>[] = [
  { key: 'type', label: 'Type', value: (a) => adjustmentTypeLabel(a.type) },
  { key: 'amount', label: 'Montant', align: 'num', value: (a) => amount(a.amount) },
  { key: 'reason', label: 'Motif' },
  { key: 'attachmentRef', label: 'Pièce jointe', value: (a) => a.attachmentRef ?? '—' },
  { key: 'correctedLineId', label: 'Ligne corrigée', value: (a) => (a.correctedLineId === null ? '—' : `#${a.correctedLineId}`) },
  { key: 'status', label: 'Statut' },
  { key: 'submitter', label: 'Soumis par' },
  { key: 'approver', label: 'Approuvé par', value: (a) => a.approver ?? '—' },
];

/** Loads the adjustments of the selected entity and month. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<AdjustmentView[]>(`/api/ui/v1/adjustments/${entity.value}/${month.value}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/** Submits a new adjustment for approval. */
async function submit(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/adjustments/${entity.value}/${month.value}`, {
      type: form.value.type,
      amount: form.value.amount,
      payeeType: 'DISTRIBUTOR',
      payeeId: form.value.payeeId,
      counterpartPayeeType: form.value.counterpartPayeeId === null ? null : 'DISTRIBUTOR',
      counterpartPayeeId: form.value.counterpartPayeeId,
      reason: form.value.reason,
      attachmentRef: form.value.attachmentRef === '' ? null : form.value.attachmentRef,
      correctedLineId: form.value.correctedLineId,
      autoReverse: form.value.autoReverse,
      repaymentSchedule: form.value.repaymentSchedule === '' ? null : JSON.stringify(form.value.repaymentSchedule),
    });
    message.value = 'Ajustement soumis.';
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Saisie refusée';
  }
}

/**
 * Approves a submitted adjustment.
 * @param a the adjustment to approve
 */
async function approve(a: AdjustmentView): Promise<void> {
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/adjustments/${a.id}/approve`, {});
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Approbation refusée';
  }
}

/**
 * Bulk-imports adjustments from a chosen pivot CSV file (REG-04), then reloads the list.
 * @param event the file input change event
 */
async function bulkImport(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files && input.files[0];
  if (!file) return;
  message.value = '';
  error.value = '';
  try {
    const content = await file.text();
    const result = await apiPost<{ imported: number }>(
      `/api/ui/v1/adjustments/${entity.value}/${month.value}/bulk`,
      { content },
    );
    message.value = `${result.imported} ajustement(s) importé(s).`;
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Import refusé';
  } finally {
    input.value = '';
  }
}

/**
 * Formats a monetary value in euros.
 * @param value the amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Ajustements"
      subtitle="Saisie, approbation par un second utilisateur et application en ligne de commission (section 8.5)."
    />

    <FilterBar
      :loading="loading"
      search-label="Rafraîchir"
      search-testid="adj-refresh"
      :resettable="false"
      @search="load"
    >
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="adj-entity" v-model="entity" />
      </FormField>
      <FormField label="Mois" stacked width="short" v-slot="{ id }">
        <MonthSelect :id="id" data-testid="adj-month" v-model="month" :entity="entity" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="adj-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="adj-message">{{ message }}</AppMessage>

    <AppCard v-if="canOperate()" title="Saisir un ajustement" data-testid="adj-form">
      <AppForm twoColumn @submit="submit">
        <FormField label="Type" width="short" v-slot="{ id }">
          <CodeSelect :id="id" data-testid="adj-type" v-model="form.type" :options="ADJUSTMENT_TYPE_OPTIONS" />
        </FormField>
        <FormField label="Montant" width="amount" v-slot="{ id }">
          <input :id="id" data-testid="adj-amount" type="number" v-model.number="form.amount" />
        </FormField>
        <FormField label="Code payé (distributeur)" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adj-payee" type="number" v-model.number="form.payeeId" />
        </FormField>
        <FormField label="Motif" width="long" v-slot="{ id }">
          <input :id="id" data-testid="adj-reason" type="text" v-model="form.reason" />
        </FormField>
        <FormField label="Pièce jointe (référence)" width="long" v-slot="{ id }">
          <input :id="id" data-testid="adj-attachment" type="text" v-model="form.attachmentRef" />
        </FormField>
        <FormField label="Ligne corrigée (identifiant)" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adj-corrected-line" type="number" v-model.number="form.correctedLineId" />
        </FormField>
        <FormField label="Compte de contrepartie (transfert)" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adj-counterpart" type="number" v-model.number="form.counterpartPayeeId" />
        </FormField>
        <FormField label="Contrepassation" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adj-reverse" type="checkbox" v-model="form.autoReverse" />
        </FormField>
        <FormField label="Échéancier d'apurement" width="long" v-slot="{ id }">
          <input :id="id" data-testid="adj-schedule" type="text" v-model="form.repaymentSchedule"
                 placeholder="ex. 3 × 100 € mensuels" />
        </FormField>
        <AppButton type="submit" data-testid="adj-submit">Soumettre</AppButton>
      </AppForm>
    </AppCard>

    <AppCard v-if="canOperate()" title="Import en masse d'ajustements" data-testid="adj-bulk">
      <AppForm>
        <FormField label="Fichier pivot (CSV)" width="long" v-slot="{ id }">
          <input :id="id" data-testid="adj-bulk-file" type="file" accept=".csv,text/csv" @change="bulkImport" />
        </FormField>
      </AppForm>
    </AppCard>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        row-key="id"
        empty="Aucun ajustement."
        empty-testid="no-adjustment"
        :row-testid="(a) => `adjustment-row-${a.id}`"
      >
        <template #cell-status="{ row }">
          <span :data-testid="`adjustment-status-${row.id}`">{{ adjustmentStatusLabel(row.status) }}</span>
        </template>
        <template #actions="{ row }">
          <AppButton
            v-if="canApprove() && row.status === 'SUBMITTED'"
            variant="secondary"
            :data-testid="`adjustment-approve-${row.id}`"
            @click="approve(row)"
          >
            Approuver
          </AppButton>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>
