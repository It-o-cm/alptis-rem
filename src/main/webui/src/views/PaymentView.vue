<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle, canValidateFinance, paymentStatusLabel } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';

interface LotView {
  id: number;
  batchType: string;
  status: string;
  reference: string;
  total: number;
  orders: number;
  exclusions: number;
}

interface OrderView {
  id: number;
  payeeType: string;
  payeeId: number;
  amount: number;
  status: string;
  iban: string | null;
  endToEndId: string | null;
}

interface ExclusionView {
  payeeType: string;
  payeeId: number;
  reason: string;
  amount: number;
}

interface LotDetail {
  id: number;
  batchType: string;
  status: string;
  total: number;
  orders: OrderView[];
  exclusions: ExclusionView[];
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const month = ref('2026-09-01');
const lots = ref<LotView[]>([]);
const detail = ref<LotDetail | null>(null);
const sepa = ref('');
const error = ref('');
const message = ref('');
const offCyclePartner = ref('');

const canOperate = () => canOperateCycle(session.roles);
const canFinance = () => canValidateFinance(session.roles);

function statusLabel(status: string): string {
  return paymentStatusLabel(status);
}

function amount(value: number): string {
  return formatAmount(value);
}

async function loadLots() {
  error.value = '';
  detail.value = null;
  sepa.value = '';
  try {
    lots.value = await apiGet<LotView[]>(`/api/ui/v1/payment/${entity.value}/${month.value}/lots`);
  } catch (e) {
    lots.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function openLot(id: number) {
  error.value = '';
  sepa.value = '';
  try {
    detail.value = await apiGet<LotDetail>(`/api/ui/v1/payment/lots/${id}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Lot introuvable';
  }
}

async function prepare() {
  if (!detail.value) return;
  message.value = '';
  error.value = '';
  try {
    detail.value = await apiPost<LotDetail>(`/api/ui/v1/payment/lots/${detail.value.id}/prepare`, {});
    message.value = 'Lot préparé.';
    await loadLotsKeepingDetail();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Préparation refusée';
  }
}

async function validate() {
  if (!detail.value) return;
  message.value = '';
  error.value = '';
  try {
    detail.value = await apiPost<LotDetail>(`/api/ui/v1/payment/lots/${detail.value.id}/validate`, {});
    message.value = 'Lot validé pour la Finance.';
    await loadLotsKeepingDetail();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Validation refusée';
  }
}

async function showSepa() {
  if (!detail.value) return;
  error.value = '';
  try {
    sepa.value = await apiGetText(`/api/ui/v1/payment/lots/${detail.value.id}/sepa`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Fichier SEPA indisponible';
  }
}

async function recordResult(order: OrderView, settled: boolean) {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/payment/orders/${order.id}/result`, {
      settled,
      reason: settled ? null : 'Rejet banque',
    });
    message.value = settled ? 'Ordre réglé.' : 'Ordre rejeté.';
    if (detail.value) {
      await openLot(detail.value.id);
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Résultat refusé';
  }
}

async function createOffCycle() {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/payment/${entity.value}/${month.value}/off-cycle/${offCyclePartner.value}`, {});
    message.value = 'Lot hors-cycle créé.';
    await loadLots();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Création refusée';
  }
}

async function loadLotsKeepingDetail() {
  const openId = detail.value?.id;
  try {
    lots.value = await apiGet<LotView[]>(`/api/ui/v1/payment/${entity.value}/${month.value}/lots`);
  } catch {
    // keep the current list on a transient failure
  }
  if (openId) {
    await openLot(openId);
  }
}

onMounted(loadLots);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Paiement</h1>
      <div class="page__subtitle">
        Lots de paiement d'une période, préparation, validation Finance et fichier SEPA, résultat bancaire
        et lot hors-cycle (sections 10.2 à 10.4).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="pay-entity" type="text" v-model="entity" /></label>
        <label>Mois <input data-testid="pay-month" type="text" v-model="month" /></label>
        <button class="btn" data-testid="pay-refresh" @click="loadLots">Rafraîchir</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="pay-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="pay-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <section v-if="canOperate()" class="card" style="padding: 18px 20px" data-testid="pay-offcycle-form">
      <h2 style="margin: 0 0 12px; font-size: 16px">Lot hors-cycle</h2>
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Code distributeur <input data-testid="pay-offcycle-partner" type="text" v-model="offCyclePartner" /></label>
        <button class="btn" data-testid="pay-offcycle-submit" @click="createOffCycle">Créer le lot</button>
      </div>
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Référence</th><th>Type</th><th>Statut</th><th>Total</th><th>Ordres</th><th>Exclusions</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="lots.length === 0"><td colspan="7" data-testid="no-lot">Aucun lot.</td></tr>
          <tr v-for="l in lots" :key="l.id" :data-testid="`lot-row-${l.id}`">
            <td>{{ l.reference }}</td>
            <td :data-testid="`lot-type-${l.id}`">{{ l.batchType }}</td>
            <td :data-testid="`lot-status-${l.id}`">{{ statusLabel(l.status) }}</td>
            <td>{{ amount(l.total) }}</td>
            <td>{{ l.orders }}</td>
            <td>{{ l.exclusions }}</td>
            <td><button class="btn" :data-testid="`lot-open-${l.id}`" @click="openLot(l.id)">Détail</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="detail" class="card" style="padding: 18px 20px" data-testid="lot-detail">
      <h2 style="margin: 0 0 12px; font-size: 16px">
        Lot {{ detail.batchType }} — {{ statusLabel(detail.status) }} — {{ amount(detail.total) }}
      </h2>
      <div style="display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 12px">
        <button v-if="canOperate() && detail.status === 'DRAFT'" class="btn" data-testid="lot-prepare" @click="prepare">
          Préparer
        </button>
        <button v-if="canFinance() && detail.status === 'DRAFT'" class="btn" data-testid="lot-validate" @click="validate">
          Valider (Finance)
        </button>
        <button v-if="canFinance() && detail.status === 'ISSUED'" class="btn" data-testid="lot-sepa" @click="showSepa">
          Fichier SEPA
        </button>
      </div>

      <h3 style="margin: 12px 0 8px; font-size: 14px">Ordres</h3>
      <table>
        <thead>
          <tr><th>Bénéficiaire</th><th>Montant</th><th>IBAN</th><th>Référence</th><th>Statut</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="detail.orders.length === 0"><td colspan="6" data-testid="no-order">Aucun ordre.</td></tr>
          <tr v-for="o in detail.orders" :key="o.id" :data-testid="`order-row-${o.id}`">
            <td>{{ o.payeeType }} #{{ o.payeeId }}</td>
            <td>{{ amount(o.amount) }}</td>
            <td :data-testid="`order-iban-${o.id}`">{{ o.iban ?? '—' }}</td>
            <td>{{ o.endToEndId ?? '—' }}</td>
            <td :data-testid="`order-status-${o.id}`">{{ statusLabel(o.status) }}</td>
            <td>
              <template v-if="canFinance() && o.status === 'ISSUED'">
                <button class="btn" :data-testid="`order-settle-${o.id}`" @click="recordResult(o, true)">Réglé</button>
                <button class="btn" :data-testid="`order-reject-${o.id}`" @click="recordResult(o, false)">Rejeté</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>

      <h3 style="margin: 16px 0 8px; font-size: 14px">Exclusions</h3>
      <table>
        <thead><tr><th>Bénéficiaire</th><th>Motif</th><th>Montant</th></tr></thead>
        <tbody>
          <tr v-if="detail.exclusions.length === 0"><td colspan="3" data-testid="no-exclusion">Aucune exclusion.</td></tr>
          <tr v-for="(x, i) in detail.exclusions" :key="i" :data-testid="`exclusion-row-${i}`">
            <td>{{ x.payeeType }} #{{ x.payeeId }}</td>
            <td :data-testid="`exclusion-reason-${i}`">{{ x.reason }}</td>
            <td>{{ amount(x.amount) }}</td>
          </tr>
        </tbody>
      </table>

      <pre v-if="sepa" data-testid="sepa-content" style="margin-top: 12px; overflow-x: auto; background: #f6f8fa; padding: 12px">{{ sepa }}</pre>
    </section>
  </div>
</template>
