<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';

interface AgreementView {
  entityCode: string; type: string | null; partnerCode: string | null; groupCode: string | null;
  reference: string; signature: string | null;
}
interface EnrollmentView { partnerCode: string | null; groupCode: string | null; agreementRef: string | null; status: string | null; }

const session = useSessionStore();
const entityCode = () => (session.entities.length > 0 ? session.entities[0] : '');
const canEdit = () => canEditEntityParameters(session.roles);

const agreements = ref<AgreementView[]>([]);
const enrollments = ref<EnrollmentView[]>([]);
const error = ref('');
const message = ref('');
const agForm = ref({ type: 'PARTNERSHIP', partnerCode: '', reference: '', validFrom: '', signed: true });
const enForm = ref({ planCode: '', ruleCode: '', partnerCode: '', agreementRef: '', validFrom: '' });

function fail(e: unknown) {
  error.value = e instanceof ApiError ? e.message : 'Erreur';
  message.value = '';
}

async function loadAgreements() {
  try {
    agreements.value = await apiGet<AgreementView[]>('/api/ui/v1/agreements');
  } catch (e) {
    fail(e);
  }
}

async function createAgreement() {
  try {
    await apiPost('/api/ui/v1/agreements', {
      entityCode: entityCode(), type: agForm.value.type, partnerCode: agForm.value.partnerCode || null,
      reference: agForm.value.reference, validFrom: agForm.value.validFrom || null, signed: agForm.value.signed,
    });
    message.value = 'Accord enregistré.';
    error.value = '';
    await loadAgreements();
  } catch (e) {
    fail(e);
  }
}

async function loadEnrollments() {
  if (!enForm.value.planCode || !enForm.value.ruleCode) {
    return;
  }
  try {
    enrollments.value = await apiGet<EnrollmentView[]>(
      `/api/ui/v1/plans/${enForm.value.planCode}/rules/${enForm.value.ruleCode}/enrollments?entityCode=${entityCode()}`);
  } catch (e) {
    fail(e);
  }
}

async function enroll() {
  try {
    await apiPost(`/api/ui/v1/plans/${enForm.value.planCode}/rules/${enForm.value.ruleCode}/enrollments`, {
      entityCode: entityCode(), partnerCode: enForm.value.partnerCode, agreementRef: enForm.value.agreementRef,
      validFrom: enForm.value.validFrom || null,
    });
    message.value = 'Inscription enregistrée.';
    error.value = '';
    await loadEnrollments();
  } catch (e) {
    fail(e);
  }
}

onMounted(loadAgreements);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Accords et inscriptions</h1>
      <div class="page__subtitle">Accords fondateurs et inscriptions aux règles réservées, actives une fois l'accord signé (PAR-10).</div>
    </div>
    <div v-if="error" class="card" style="padding: 12px 16px; color: #8f1d16" data-testid="enroll-error">{{ error }}</div>
    <div v-if="message" class="card" style="padding: 12px 16px; color: #1d6b2f" data-testid="enroll-message">{{ message }}</div>

    <section class="card" style="overflow-x: auto">
      <h2 style="margin: 12px 16px">Accords</h2>
      <table>
        <thead><tr><th>Référence</th><th>Type</th><th>Distributeur</th><th>Signature</th></tr></thead>
        <tbody>
          <tr v-if="agreements.length === 0"><td colspan="4" data-testid="agreements-empty">Aucun accord.</td></tr>
          <tr v-for="(a, i) in agreements" :key="i" :data-testid="`agreement-row-${a.reference}`">
            <td style="font-family: 'IBM Plex Mono', monospace">{{ a.reference }}</td>
            <td>{{ a.type ?? '—' }}</td>
            <td>{{ a.partnerCode ?? a.groupCode ?? '—' }}</td>
            <td><span :class="a.signature === 'SIGNED' ? 'tag ok' : 'tag mute'">{{ a.signature ?? '—' }}</span></td>
          </tr>
        </tbody>
      </table>
      <div v-if="canEdit()" style="padding: 12px 16px; display: flex; gap: 8px; flex-wrap: wrap; align-items: end">
        <label>Référence<input data-testid="ag-ref" v-model="agForm.reference" /></label>
        <label>Distributeur<input data-testid="ag-partner" v-model="agForm.partnerCode" /></label>
        <label>Du<input data-testid="ag-from" type="date" v-model="agForm.validFrom" /></label>
        <label>Signé<input data-testid="ag-signed" type="checkbox" v-model="agForm.signed" /></label>
        <button class="btn" data-testid="ag-create" @click="createAgreement">Enregistrer l'accord</button>
      </div>
    </section>

    <section v-if="canEdit()" class="card" style="padding: 12px 16px" data-testid="enroll-section">
      <h2>Inscription à une règle réservée</h2>
      <div style="display: flex; gap: 8px; flex-wrap: wrap; align-items: end">
        <label>Plan<input data-testid="en-plan" v-model="enForm.planCode" /></label>
        <label>Règle<input data-testid="en-rule" v-model="enForm.ruleCode" /></label>
        <label>Distributeur<input data-testid="en-partner" v-model="enForm.partnerCode" /></label>
        <label>Accord<input data-testid="en-ref" v-model="enForm.agreementRef" /></label>
        <label>Du<input data-testid="en-from" type="date" v-model="enForm.validFrom" /></label>
        <button class="btn" data-testid="en-enroll" @click="enroll">Inscrire</button>
        <button class="btn" data-testid="en-load" @click="loadEnrollments">Voir les inscriptions</button>
      </div>
      <table style="margin-top: 10px">
        <thead><tr><th>Distributeur</th><th>Groupement</th><th>Accord</th><th>Statut</th></tr></thead>
        <tbody>
          <tr v-if="enrollments.length === 0"><td colspan="4" data-testid="enrollments-empty">Aucune inscription chargée.</td></tr>
          <tr v-for="(e, i) in enrollments" :key="i" :data-testid="`enrollment-row-${i}`">
            <td>{{ e.partnerCode ?? '—' }}</td>
            <td>{{ e.groupCode ?? '—' }}</td>
            <td>{{ e.agreementRef ?? '—' }}</td>
            <td><span :class="e.status === 'ACTIVE' ? 'tag ok' : 'tag mute'">{{ e.status ?? '—' }}</span></td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
