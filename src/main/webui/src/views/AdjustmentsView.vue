<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';

interface AdjustmentView {
  id: number;
  type: string;
  amount: number;
  reason: string;
  status: string;
  submitter: string;
  approver: string | null;
}

const TYPES = ['EXCEPTIONAL_ADVANCE', 'MANUAL_CLAWBACK', 'CORRECTION', 'EXCEPTIONAL_COMMISSION', 'ACCOUNT_TRANSFER'];

const session = useSessionStore();
const entity = ref(session.entities.length > 0 ? session.entities[0] : '');
const month = ref('2026-09-01');
const rows = ref<AdjustmentView[]>([]);
const error = ref('');
const message = ref('');
const form = ref({ type: 'CORRECTION', amount: 0, payeeId: 0, reason: '', autoReverse: false });

const canOperate = () => canOperateCycle(session.roles);

async function load() {
  error.value = '';
  try {
    rows.value = await apiGet<AdjustmentView[]>(`/api/ui/v1/adjustments/${entity.value}/${month.value}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function submit() {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/adjustments/${entity.value}/${month.value}`, {
      type: form.value.type,
      amount: form.value.amount,
      payeeType: 'DISTRIBUTOR',
      payeeId: form.value.payeeId,
      reason: form.value.reason,
      autoReverse: form.value.autoReverse,
    });
    message.value = 'Ajustement soumis.';
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Saisie refusée';
  }
}

async function approve(a: AdjustmentView) {
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/adjustments/${a.id}/approve`, {});
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Approbation refusée';
  }
}

function amount(value: number): string {
  return formatAmount(value);
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Ajustements</h1>
      <div class="page__subtitle">
        Saisie, approbation par un second utilisateur et application en ligne de commission (section 8.5).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="adj-entity" type="text" v-model="entity" /></label>
        <label>Mois <input data-testid="adj-month" type="text" v-model="month" /></label>
        <button class="btn" data-testid="adj-refresh" @click="load">Rafraîchir</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="adj-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="adj-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <section v-if="canOperate()" class="card" style="padding: 18px 20px" data-testid="adj-form">
      <h2 style="margin: 0 0 12px; font-size: 16px">Saisir un ajustement</h2>
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Type
          <select data-testid="adj-type" v-model="form.type">
            <option v-for="t in TYPES" :key="t" :value="t">{{ t }}</option>
          </select>
        </label>
        <label>Montant <input data-testid="adj-amount" type="number" v-model.number="form.amount" /></label>
        <label>Code payé (distributeur) <input data-testid="adj-payee" type="number" v-model.number="form.payeeId" /></label>
        <label>Motif <input data-testid="adj-reason" type="text" v-model="form.reason" /></label>
        <label>Contrepassation <input data-testid="adj-reverse" type="checkbox" v-model="form.autoReverse" /></label>
        <button class="btn" data-testid="adj-submit" @click="submit">Soumettre</button>
      </div>
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Type</th><th>Montant</th><th>Motif</th><th>Statut</th><th>Soumis par</th><th>Approuvé par</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="7" data-testid="no-adjustment">Aucun ajustement.</td></tr>
          <tr v-for="a in rows" :key="a.id" :data-testid="`adjustment-row-${a.id}`">
            <td>{{ a.type }}</td>
            <td>{{ amount(a.amount) }}</td>
            <td>{{ a.reason }}</td>
            <td :data-testid="`adjustment-status-${a.id}`">{{ a.status }}</td>
            <td>{{ a.submitter }}</td>
            <td>{{ a.approver ?? '—' }}</td>
            <td>
              <button v-if="canOperate() && a.status === 'SUBMITTED'" class="btn"
                      :data-testid="`adjustment-approve-${a.id}`" @click="approve(a)">Approuver</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
