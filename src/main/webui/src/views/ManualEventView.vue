<script setup lang="ts">
import { ref } from 'vue';
import { apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';

const EVENT_TYPES = [
  'PREMIUM_COLLECTED', 'PREMIUM_CANCELLED', 'TERMINATION', 'WITHDRAWAL', 'DEATH',
  'SUBSCRIPTION', 'ENDORSEMENT', 'SCHEDULED_PAYMENT', 'INITIAL_PAYMENT', 'SURRENDER',
];

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

const entityCode = () => (session.entities.length > 0 ? session.entities[0] : '');

async function submit() {
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
    if (result.rejected > 0) {
      error.value = 'Événement rejeté : vérifiez les champs (montant, date, champs obligatoires).';
    } else if (result.onHold > 0) {
      message.value = 'Événement enregistré en attente : une référence est encore inconnue.';
    } else {
      message.value = 'Événement enregistré ; il sera pris par le prochain calcul.';
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : "Échec de l'enregistrement";
  }
}
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Saisie d'un événement</h1>
      <div class="page__subtitle">Saisie manuelle, tracée, contrôlée comme un import ; reprise par le prochain calcul (ING-07).</div>
    </div>

    <section class="card" style="padding: 18px 20px" data-testid="manual-form">
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px">
        <label>Système source<input data-testid="field-source" type="text" v-model="form.sourceSystem" /></label>
        <label>Clé source<input data-testid="field-key" type="text" v-model="form.sourceKey" /></label>
        <label>Type d'événement
          <select data-testid="field-type" v-model="form.eventType">
            <option v-for="t in EVENT_TYPES" :key="t" :value="t">{{ t }}</option>
          </select>
        </label>
        <label>Numéro d'adhésion<input data-testid="field-contract" type="text" v-model="form.contractNumber" /></label>
        <label>Code garantie<input data-testid="field-coverage" type="text" v-model="form.coverageCode" /></label>
        <label>Date d'effet<input data-testid="field-date" type="date" v-model="form.eventDate" /></label>
        <label>Montant TTC<input data-testid="field-amount" type="number" step="0.01" v-model="form.grossAmount" /></label>
      </div>
      <div style="margin-top: 14px; display: flex; gap: 10px; align-items: center">
        <button class="btn" data-testid="manual-submit" @click="submit">Enregistrer l'événement</button>
        <span v-if="message" class="tag ok" data-testid="manual-message">{{ message }}</span>
        <span v-if="error" class="tag ko" data-testid="manual-error">{{ error }}</span>
      </div>
    </section>
  </div>
</template>
