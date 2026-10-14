<script setup lang="ts">
import { ref } from 'vue';
import { apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import {
  AppButton,
  AppCard,
  AppForm,
  AppMessage,
  CodeSelect,
  FormField,
  PageHeading,
  ProductSelect,
  SourceSystemSelect,
} from '../components/ui';
import { eventTypeLabel } from '../presentation/events';
import { REMUNERATION_MODE_OPTIONS, CONTRACT_STATUS_OPTIONS } from '../presentation/coverage';

// The event types a manual entry may declare, each shown by its French label (ADM-03).
const EVENT_TYPE_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  'PREMIUM_COLLECTED', 'PREMIUM_CANCELLED', 'TERMINATION', 'WITHDRAWAL', 'DEATH',
  'SUBSCRIPTION', 'ENDORSEMENT', 'SCHEDULED_PAYMENT', 'INITIAL_PAYMENT', 'SURRENDER',
].map((value) => ({ value, label: eventTypeLabel(value) }));

const session = useSessionStore();
const form = ref({
  sourceSystem: 'MANUAL',
  sourceKey: '',
  eventType: 'PREMIUM_COLLECTED',
  contractNumber: '',
  coverageCode: '',
  eventDate: '',
  grossAmount: '',
});
const message = ref('');
const error = ref('');

const coverage = ref({
  sourceSystem: 'MANUAL',
  contractNumber: '',
  coverageCode: '',
  productCode: '',
  policyholderId: '',
  productionCode: '',
  remunerationMode: 'LINEAR',
  status: 'IN_FORCE',
  registrationDate: '',
  effectDate: '',
  annualPremium: '',
});
const coverageMessage = ref('');
const coverageError = ref('');

/** The user's first entity, or an empty string when none. */
const entityCode = (): string => (session.entities.length > 0 ? session.entities[0] : '');

/** Maps a journalled outcome to a French message, or null when rejected. */
function outcomeMessage(result: { loaded: number; rejected: number; onHold: number }, noun: string): string | null {
  if (result.rejected > 0) return null;
  if (result.onHold > 0) return `${noun} enregistré(e) en attente : une référence est encore inconnue.`;
  return `${noun} enregistré(e) ; sera pris(e) par le prochain calcul.`;
}

/** Submits the manual event, mapping the server outcome to a French message. */
async function submit(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ loaded: number; rejected: number; onHold: number }>('/api/ui/v1/events', {
      entityCode: entityCode(),
      sourceSystem: form.value.sourceSystem,
      fields: {
        source_key: form.value.sourceKey,
        event_type: form.value.eventType,
        contract_number: form.value.contractNumber,
        coverage_code: form.value.coverageCode,
        event_date: form.value.eventDate,
        gross_amount: form.value.grossAmount,
      },
    });
    const ok = outcomeMessage(result, 'Événement');
    if (ok) message.value = ok;
    else error.value = 'Événement rejeté : vérifiez les champs (montant, date, champs obligatoires).';
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : "Échec de l'enregistrement";
  }
}

/** Submits the manual complementary datum (a coverage), mapping the outcome to a French message. */
async function submitCoverage(): Promise<void> {
  coverageMessage.value = '';
  coverageError.value = '';
  try {
    const result = await apiPost<{ loaded: number; rejected: number; onHold: number }>(
      '/api/ui/v1/events/complementary-data',
      {
        entityCode: entityCode(),
        sourceSystem: coverage.value.sourceSystem,
        fields: {
          contract_number: coverage.value.contractNumber,
          coverage_code: coverage.value.coverageCode,
          product_code: coverage.value.productCode,
          policyholder_id: coverage.value.policyholderId,
          production_code: coverage.value.productionCode,
          remuneration_mode: coverage.value.remunerationMode,
          status: coverage.value.status,
          registration_date: coverage.value.registrationDate,
          effect_date: coverage.value.effectDate,
          annual_premium: coverage.value.annualPremium,
        },
      },
    );
    const ok = outcomeMessage(result, 'Donnée complémentaire');
    if (ok) coverageMessage.value = ok;
    else coverageError.value = 'Donnée rejetée : vérifiez les champs obligatoires, le statut et le mode.';
  } catch (e) {
    coverageError.value = e instanceof ApiError ? e.message : "Échec de l'enregistrement";
  }
}
</script>

<template>
  <div class="page">
    <PageHeading
      title="Saisie manuelle"
      subtitle="Saisie manuelle d'un événement ou d'une donnée complémentaire, tracée et contrôlée comme un import ; reprise par le prochain calcul (ING-07)."
    />

    <AppCard data-testid="manual-form" title="Saisie d'un événement">
      <AppMessage v-if="message" variant="success" data-testid="manual-message">{{ message }}</AppMessage>
      <AppMessage v-if="error" variant="error" data-testid="manual-error">{{ error }}</AppMessage>
      <AppForm twoColumn @submit="submit">
        <FormField label="Système source" width="short" v-slot="{ id }">
          <SourceSystemSelect :id="id" data-testid="field-source" v-model="form.sourceSystem" />
        </FormField>
        <FormField label="Clé source" width="short" v-slot="{ id }">
          <input :id="id" data-testid="field-key" type="text" v-model="form.sourceKey" />
        </FormField>
        <FormField label="Type d'événement" width="short" v-slot="{ id }">
          <CodeSelect :id="id" data-testid="field-type" v-model="form.eventType" :options="EVENT_TYPE_OPTIONS" />
        </FormField>
        <FormField label="Numéro d'adhésion" width="short" v-slot="{ id }">
          <input :id="id" data-testid="field-contract" type="text" v-model="form.contractNumber" />
        </FormField>
        <FormField label="Code garantie" width="short" v-slot="{ id }">
          <input :id="id" data-testid="field-coverage" type="text" v-model="form.coverageCode" />
        </FormField>
        <FormField label="Date d'effet" width="date" v-slot="{ id }">
          <input :id="id" data-testid="field-date" type="date" v-model="form.eventDate" />
        </FormField>
        <FormField label="Montant TTC" width="amount" v-slot="{ id }">
          <input :id="id" data-testid="field-amount" type="number" step="0.01" v-model="form.grossAmount" />
        </FormField>
        <AppButton type="submit" data-testid="manual-submit">Enregistrer l'événement</AppButton>
      </AppForm>
    </AppCard>

    <AppCard data-testid="manual-coverage-form" title="Saisie d'une donnée complémentaire (garantie)">
      <AppMessage v-if="coverageMessage" variant="success" data-testid="coverage-message">{{ coverageMessage }}</AppMessage>
      <AppMessage v-if="coverageError" variant="error" data-testid="coverage-error">{{ coverageError }}</AppMessage>
      <AppForm twoColumn @submit="submitCoverage">
        <FormField label="Système source" width="short" v-slot="{ id }">
          <SourceSystemSelect :id="id" data-testid="cov-source" v-model="coverage.sourceSystem" />
        </FormField>
        <FormField label="Numéro d'adhésion" width="short" v-slot="{ id }">
          <input :id="id" data-testid="cov-contract" type="text" v-model="coverage.contractNumber" />
        </FormField>
        <FormField label="Code garantie" width="short" v-slot="{ id }">
          <input :id="id" data-testid="cov-coverage" type="text" v-model="coverage.coverageCode" />
        </FormField>
        <FormField label="Code produit" width="short" v-slot="{ id }">
          <ProductSelect :id="id" data-testid="cov-product" v-model="coverage.productCode" />
        </FormField>
        <FormField label="Identifiant souscripteur" width="short" v-slot="{ id }">
          <input :id="id" data-testid="cov-holder" type="text" v-model="coverage.policyholderId" />
        </FormField>
        <FormField label="Code de production" width="short" v-slot="{ id }">
          <input :id="id" data-testid="cov-production" type="text" v-model="coverage.productionCode" />
        </FormField>
        <FormField label="Mode de rémunération" width="short" v-slot="{ id }">
          <CodeSelect :id="id" data-testid="cov-mode" v-model="coverage.remunerationMode" :options="REMUNERATION_MODE_OPTIONS" />
        </FormField>
        <FormField label="Statut" width="short" v-slot="{ id }">
          <CodeSelect :id="id" data-testid="cov-status" v-model="coverage.status" :options="CONTRACT_STATUS_OPTIONS" />
        </FormField>
        <FormField label="Date d'enregistrement" width="date" v-slot="{ id }">
          <input :id="id" data-testid="cov-registration" type="date" v-model="coverage.registrationDate" />
        </FormField>
        <FormField label="Date d'effet" width="date" v-slot="{ id }">
          <input :id="id" data-testid="cov-effect" type="date" v-model="coverage.effectDate" />
        </FormField>
        <FormField label="Prime annuelle" width="amount" v-slot="{ id }">
          <input :id="id" data-testid="cov-premium" type="number" step="0.01" v-model="coverage.annualPremium" />
        </FormField>
        <AppButton type="submit" data-testid="coverage-submit">Enregistrer la donnée</AppButton>
      </AppForm>
    </AppCard>
  </div>
</template>
