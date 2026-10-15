<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPut, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { formatAmount } from '../presentation/format';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface EntityView {
  code: string;
  name: string;
  minPaymentAmount: number | null;
  advanceSettlementDays: number | null;
  advanceSettlementTolerance: number | null;
  paymentCalendar: string | null;
  adjustmentApprovalThreshold: number | null;
  version: number;
}

const session = useSessionStore();
const entities = ref<EntityView[]>([]);
const loading = ref(true);
const loadError = ref('');
const selected = ref<EntityView | null>(null);
const form = ref({ minPaymentAmount: 0, advanceSettlementDays: 0, advanceSettlementTolerance: 0, paymentCalendar: '', adjustmentApprovalThreshold: 0 });
const message = ref('');
const formError = ref('');

/** Whether the current user may edit entity parameters. */
const canEdit = () => canEditEntityParameters(session.roles);

/** Columns of the entities table. */
const columns: Column<EntityView>[] = [
  { key: 'name', label: 'Entité' },
  { key: 'code', label: 'Code' },
  { key: 'minPaymentAmount', label: 'Seuil de paiement', align: 'num', value: (e) => formatAmount(e.minPaymentAmount) },
  { key: 'advanceSettlementDays', label: 'Délai solde (j)', align: 'num' },
  {
    key: 'advanceSettlementTolerance',
    label: 'Tolérance',
    align: 'num',
    value: (e) => formatAmount(e.advanceSettlementTolerance),
  },
];

/** Loads the entities visible to the user. */
async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    entities.value = await apiGet<EntityView[]>('/api/ui/v1/entities');
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

/**
 * Selects an entity and fills the edit form with its parameters.
 * @param entity the selected entity
 */
function select(entity: EntityView) {
  selected.value = entity;
  message.value = '';
  formError.value = '';
  form.value = {
    minPaymentAmount: entity.minPaymentAmount ?? 0,
    advanceSettlementDays: entity.advanceSettlementDays ?? 0,
    advanceSettlementTolerance: entity.advanceSettlementTolerance ?? 0,
    paymentCalendar: entity.paymentCalendar ?? '',
    adjustmentApprovalThreshold: entity.adjustmentApprovalThreshold ?? 0,
  };
}

/** Saves the edited parameters of the selected entity. */
async function save() {
  if (!selected.value) {
    return;
  }
  message.value = '';
  formError.value = '';
  try {
    const updated = await apiPut<EntityView>(
      `/api/ui/v1/entities/${selected.value.code}`,
      {
        minPaymentAmount: form.value.minPaymentAmount,
        advanceSettlementDays: form.value.advanceSettlementDays,
        advanceSettlementTolerance: form.value.advanceSettlementTolerance,
        paymentCalendar: form.value.paymentCalendar,
        adjustmentApprovalThreshold: form.value.adjustmentApprovalThreshold,
      },
      { 'X-Version': String(selected.value.version) },
    );
    message.value = 'Paramètres enregistrés.';
    selected.value = updated;
    await reload();
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : "Échec de l'enregistrement";
  }
}

onMounted(reload);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Entités et paramètres"
      subtitle="Calendrier, seuil minimal de paiement et tolérances, par entité (ADM-05)."
    />

    <AppMessage v-if="loadError" variant="error" data-testid="load-error">{{ loadError }}</AppMessage>

    <AppCard v-else flush>
      <DataTable
        :columns="columns"
        :rows="entities"
        :loading="loading"
        row-key="code"
        empty="Aucune entité visible."
        empty-testid="empty"
        clickable
        :row-testid="(e) => `entity-row-${e.code}`"
        @row-click="select"
      >
        <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
      </DataTable>
    </AppCard>

    <AppCard v-if="selected" :title="`Paramètres de ${selected.name}`" data-testid="entity-form">
      <AppForm @submit="save">
        <FormField label="Seuil minimal de paiement" width="amount" v-slot="{ id }">
          <input :id="id" data-testid="field-min-payment" type="number" step="0.01" v-model.number="form.minPaymentAmount" :disabled="!canEdit()" />
        </FormField>
        <FormField label="Délai de solde de l'escompte (jours)" width="short" v-slot="{ id }">
          <input :id="id" data-testid="field-settlement-days" type="number" v-model.number="form.advanceSettlementDays" :disabled="!canEdit()" />
        </FormField>
        <FormField label="Tolérance de solde" width="amount" v-slot="{ id }">
          <input :id="id" data-testid="field-tolerance" type="number" step="0.01" v-model.number="form.advanceSettlementTolerance" :disabled="!canEdit()" />
        </FormField>
        <FormField label="Calendrier de paiement (JSON)" width="long" v-slot="{ id }">
          <input :id="id" data-testid="field-calendar" type="text" v-model="form.paymentCalendar" :disabled="!canEdit()" />
        </FormField>
        <FormField label="Seuil d'approbation des ajustements" width="amount" v-slot="{ id }">
          <input :id="id" data-testid="field-approval-threshold" type="number" step="0.01" v-model.number="form.adjustmentApprovalThreshold" :disabled="!canEdit()" />
        </FormField>
        <AppButton v-if="canEdit()" type="submit" data-testid="save-entity">Enregistrer</AppButton>
      </AppForm>
      <AppMessage v-if="message" variant="success" data-testid="save-message">{{ message }}</AppMessage>
      <AppMessage v-if="formError" variant="error" data-testid="form-error">{{ formError }}</AppMessage>
    </AppCard>
  </div>
</template>
