<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPut, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { formatAmount } from '../presentation/format';

interface EntityView {
  code: string;
  name: string;
  minPaymentAmount: number | null;
  advanceSettlementDays: number | null;
  advanceSettlementTolerance: number | null;
  paymentCalendar: string | null;
  version: number;
}

const session = useSessionStore();
const entities = ref<EntityView[]>([]);
const loading = ref(true);
const loadError = ref('');
const selected = ref<EntityView | null>(null);
const form = ref({ minPaymentAmount: 0, advanceSettlementDays: 0, advanceSettlementTolerance: 0, paymentCalendar: '' });
const message = ref('');
const formError = ref('');

const canEdit = () => canEditEntityParameters(session.roles);

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

function select(entity: EntityView) {
  selected.value = entity;
  message.value = '';
  formError.value = '';
  form.value = {
    minPaymentAmount: entity.minPaymentAmount ?? 0,
    advanceSettlementDays: entity.advanceSettlementDays ?? 0,
    advanceSettlementTolerance: entity.advanceSettlementTolerance ?? 0,
    paymentCalendar: entity.paymentCalendar ?? '',
  };
}

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
    <div>
      <h1 class="page__title" data-testid="page-title">Entités et paramètres</h1>
      <div class="page__subtitle">Calendrier, seuil minimal de paiement et tolérances, par entité (ADM-05).</div>
    </div>

    <div v-if="loadError" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">
      {{ loadError }}
    </div>

    <section v-else class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Entité</th><th>Code</th><th class="num">Seuil de paiement</th><th class="num">Délai solde (j)</th><th class="num">Tolérance</th></tr>
        </thead>
        <tbody>
          <tr v-if="!loading && entities.length === 0"><td colspan="5" data-testid="empty">Aucune entité visible.</td></tr>
          <tr
            v-for="entity in entities"
            :key="entity.code"
            :data-testid="`entity-row-${entity.code}`"
            style="cursor: pointer"
            @click="select(entity)"
          >
            <td>{{ entity.name }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ entity.code }}</td>
            <td class="num">{{ formatAmount(entity.minPaymentAmount) }}</td>
            <td class="num">{{ entity.advanceSettlementDays }}</td>
            <td class="num">{{ formatAmount(entity.advanceSettlementTolerance) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="selected" class="card" style="padding: 18px 20px" data-testid="entity-form">
      <h2 style="margin: 0 0 12px; font-size: 16px">Paramètres de {{ selected.name }}</h2>
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px">
        <label>Seuil minimal de paiement
          <input data-testid="field-min-payment" type="number" step="0.01" v-model.number="form.minPaymentAmount" :disabled="!canEdit()" />
        </label>
        <label>Délai de solde de l'escompte (jours)
          <input data-testid="field-settlement-days" type="number" v-model.number="form.advanceSettlementDays" :disabled="!canEdit()" />
        </label>
        <label>Tolérance de solde
          <input data-testid="field-tolerance" type="number" step="0.01" v-model.number="form.advanceSettlementTolerance" :disabled="!canEdit()" />
        </label>
        <label>Calendrier de paiement (JSON)
          <input data-testid="field-calendar" type="text" v-model="form.paymentCalendar" :disabled="!canEdit()" />
        </label>
      </div>
      <div style="margin-top: 14px; display: flex; gap: 10px; align-items: center">
        <button v-if="canEdit()" class="btn" data-testid="save-entity" @click="save">Enregistrer</button>
        <span v-if="message" class="tag ok" data-testid="save-message">{{ message }}</span>
        <span v-if="formError" class="tag ko" data-testid="form-error">{{ formError }}</span>
      </div>
    </section>
  </div>
</template>
