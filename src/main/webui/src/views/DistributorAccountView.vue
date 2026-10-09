<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';

interface AccountView {
  id: number;
  payeeType: string;
  payeeId: number;
  balance: number;
  blocked: boolean;
  blockReason: string | null;
}

interface DistributorView {
  partnerCode: string;
  accounts: AccountView[];
  total: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 ? session.entities[0] : '');
const month = ref('2026-09-01');
const partnerCode = ref('');
const view = ref<DistributorView | null>(null);
const debtors = ref<AccountView[]>([]);
const error = ref('');
const message = ref('');

const canOperate = () => canOperateCycle(session.roles);

async function search() {
  error.value = '';
  view.value = null;
  try {
    view.value = await apiGet<DistributorView>(`/api/ui/v1/accounts/${entity.value}/distributor/${partnerCode.value}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Distributeur introuvable';
  }
}

async function loadDebtors() {
  debtors.value = await apiGet<AccountView[]>(`/api/ui/v1/accounts/${entity.value}/debtors`);
}

async function compensate() {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ transfers: number }>(
      `/api/ui/v1/accounts/${entity.value}/${month.value}/distributor/${partnerCode.value}/compensate`, {});
    message.value = `${result.transfers} ligne(s) de compensation écrite(s).`;
    await search();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Compensation refusée';
  }
}

async function block(a: AccountView) {
  await apiPost(`/api/ui/v1/accounts/${a.id}/block`, { reason: 'Blocage manuel' });
  await search();
}

async function unblock(a: AccountView) {
  await apiPost(`/api/ui/v1/accounts/${a.id}/unblock`, {});
  await search();
}

function amount(value: number): string {
  return formatAmount(value);
}

onMounted(loadDebtors);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Fiche distributeur et compte</h1>
      <div class="page__subtitle">
        Les comptes d'un distributeur, leur solde, la compensation et le blocage (section 8.8).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="acc-entity" type="text" v-model="entity" /></label>
        <label>Mois <input data-testid="acc-month" type="text" v-model="month" /></label>
        <label>Code partenaire <input data-testid="acc-partner" type="text" v-model="partnerCode" @keyup.enter="search" /></label>
        <button class="btn" data-testid="acc-search" @click="search">Rechercher</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="acc-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="acc-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <section v-if="view" class="card" style="padding: 18px 20px" data-testid="account-view">
      <h2 style="margin: 0 0 12px; font-size: 16px">
        {{ view.partnerCode }} — solde total <span data-testid="account-total">{{ amount(view.total) }}</span>
      </h2>
      <button v-if="canOperate()" class="btn" data-testid="acc-compensate" @click="compensate">Compenser</button>
      <table style="margin-top: 14px">
        <thead>
          <tr><th>Compte</th><th>Solde</th><th>Bloqué</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="a in view.accounts" :key="a.id" :data-testid="`account-row-${a.id}`">
            <td>{{ a.payeeType }} #{{ a.payeeId }}</td>
            <td :data-testid="`account-balance-${a.id}`">{{ amount(a.balance) }}</td>
            <td :data-testid="`account-blocked-${a.id}`">{{ a.blocked ? 'Oui' : 'Non' }}</td>
            <td v-if="canOperate()">
              <button v-if="!a.blocked" class="btn" :data-testid="`account-block-${a.id}`" @click="block(a)">Bloquer</button>
              <button v-else class="btn" :data-testid="`account-unblock-${a.id}`" @click="unblock(a)">Débloquer</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card" style="overflow-x: auto" data-testid="debtors">
      <h2 style="margin: 12px 20px; font-size: 16px">Comptes débiteurs</h2>
      <table>
        <thead>
          <tr><th>Compte</th><th>Solde</th></tr>
        </thead>
        <tbody>
          <tr v-if="debtors.length === 0"><td colspan="2" data-testid="no-debtor">Aucun compte débiteur.</td></tr>
          <tr v-for="a in debtors" :key="a.id" :data-testid="`debtor-row-${a.id}`">
            <td>{{ a.payeeType }} #{{ a.payeeId }}</td>
            <td>{{ amount(a.balance) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
