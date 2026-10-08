<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';

interface AdvanceView {
  entityCode: string; partnerCode: string | null; validFrom: string | null; validTo: string | null;
  anticipationDays: number | null; maxContracts: number | null; status: string | null;
}

const session = useSessionStore();
const entityCode = () => (session.entities.length > 0 ? session.entities[0] : '');
const canEdit = () => canEditEntityParameters(session.roles);

const rows = ref<AdvanceView[]>([]);
const error = ref('');
const message = ref('');
const form = ref({ partnerCode: '', validFrom: '', validTo: '', anticipationDays: 360, maxContracts: 50, unpaidRankThreshold: 2 });

async function load() {
  try {
    rows.value = await apiGet<AdvanceView[]>('/api/ui/v1/advance-agreements');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

async function create() {
  error.value = '';
  message.value = '';
  try {
    await apiPost('/api/ui/v1/advance-agreements', {
      entityCode: entityCode(), partnerCode: form.value.partnerCode,
      validFrom: form.value.validFrom || null, validTo: form.value.validTo || null,
      anticipationDays: form.value.anticipationDays, maxContracts: form.value.maxContracts,
      unpaidRankThreshold: form.value.unpaidRankThreshold,
    });
    message.value = "Convention d'escompte enregistrée.";
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Conventions d'escompte</h1>
      <div class="page__subtitle">Anticipation, plafond et rang d'impayé par distributeur (PAR-09).</div>
    </div>
    <div v-if="error" class="card" style="padding: 12px 16px; color: #8f1d16" data-testid="advance-error">{{ error }}</div>
    <div v-if="message" class="card" style="padding: 12px 16px; color: #1d6b2f" data-testid="advance-message">{{ message }}</div>
    <section class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Distributeur</th><th>Du</th><th>Au</th><th>Anticipation (j)</th><th>Plafond</th><th>Statut</th></tr></thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="6" data-testid="advance-empty">Aucune convention.</td></tr>
          <tr v-for="(a, i) in rows" :key="i" :data-testid="`advance-row-${i}`">
            <td>{{ a.partnerCode ?? '—' }}</td>
            <td>{{ a.validFrom ?? '—' }}</td>
            <td>{{ a.validTo ?? '—' }}</td>
            <td>{{ a.anticipationDays ?? '—' }}</td>
            <td>{{ a.maxContracts ?? '—' }}</td>
            <td><span class="tag ok">{{ a.status ?? '—' }}</span></td>
          </tr>
        </tbody>
      </table>
      <div v-if="canEdit()" style="padding: 12px 16px; display: flex; gap: 8px; flex-wrap: wrap; align-items: end">
        <label>Distributeur<input data-testid="adv-partner" v-model="form.partnerCode" /></label>
        <label>Du<input data-testid="adv-from" type="date" v-model="form.validFrom" /></label>
        <label>Au<input data-testid="adv-to" type="date" v-model="form.validTo" /></label>
        <label>Anticipation<input data-testid="adv-days" type="number" v-model.number="form.anticipationDays" /></label>
        <label>Plafond<input data-testid="adv-cap" type="number" v-model.number="form.maxContracts" /></label>
        <button class="btn" data-testid="adv-create" @click="create">Enregistrer</button>
      </div>
    </section>
  </div>
</template>
