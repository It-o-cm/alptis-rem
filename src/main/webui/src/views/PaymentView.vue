<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle, canValidateFinance, paymentStatusLabel, exclusionReasonLabel } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, EntitySelect, FilterBar, FormField, MonthSelect, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

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
const loading = ref(false);
const offCyclePartner = ref('');

/** Whether the current user may operate the cycle (prepare, off-cycle). */
const canOperate = (): boolean => canOperateCycle(session.roles);
/** Whether the current user may validate payments on behalf of Finance. */
const canFinance = (): boolean => canValidateFinance(session.roles);

/** Columns of the payment lots table. */
const lotColumns: Column<LotView>[] = [
  { key: 'reference', label: 'Référence' },
  { key: 'batchType', label: 'Type' },
  { key: 'status', label: 'Statut' },
  { key: 'total', label: 'Total', align: 'num', value: (l) => amount(l.total) },
  { key: 'orders', label: 'Ordres', align: 'num' },
  { key: 'exclusions', label: 'Exclusions', align: 'num' },
];

/** Columns of the orders table of a lot. */
const orderColumns: Column<OrderView>[] = [
  { key: 'payee', label: 'Bénéficiaire', value: (o) => `${o.payeeType} #${o.payeeId}` },
  { key: 'amount', label: 'Montant', align: 'num', value: (o) => amount(o.amount) },
  { key: 'iban', label: 'IBAN' },
  { key: 'endToEndId', label: 'Référence', value: (o) => o.endToEndId ?? '—' },
  { key: 'status', label: 'Statut' },
];

/** Columns of the exclusions table of a lot. */
const exclusionColumns: Column<ExclusionView>[] = [
  { key: 'payee', label: 'Bénéficiaire', value: (x) => `${x.payeeType} #${x.payeeId}` },
  { key: 'reason', label: 'Motif' },
  { key: 'amount', label: 'Montant', align: 'num', value: (x) => amount(x.amount) },
];

/**
 * French label of a payment status.
 * @param status the status code
 */
function statusLabel(status: string): string {
  return paymentStatusLabel(status);
}

/**
 * Formats a monetary value in euros.
 * @param value the amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Loads the payment lots of the selected entity and month. */
async function loadLots(): Promise<void> {
  error.value = '';
  detail.value = null;
  sepa.value = '';
  loading.value = true;
  try {
    lots.value = await apiGet<LotView[]>(`/api/ui/v1/payment/${entity.value}/${month.value}/lots`);
  } catch (e) {
    lots.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/**
 * Opens a lot and loads its detail.
 * @param id the lot id
 */
async function openLot(id: number): Promise<void> {
  error.value = '';
  sepa.value = '';
  try {
    detail.value = await apiGet<LotDetail>(`/api/ui/v1/payment/lots/${id}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Lot introuvable';
  }
}

/** Prepares the open lot. */
async function prepare(): Promise<void> {
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

/** Validates the open lot for Finance. */
async function validate(): Promise<void> {
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

/** Records the emission of the open cheque lot's cheques (PAY-04), then reloads it. */
async function chequeEmission(): Promise<void> {
  if (!detail.value) return;
  message.value = '';
  error.value = '';
  try {
    detail.value = await apiPost<LotDetail>(`/api/ui/v1/payment/lots/${detail.value.id}/cheque-emission`, {});
    message.value = 'Émission des chèques enregistrée.';
    await loadLotsKeepingDetail();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Émission refusée';
  }
}

/** Loads the SEPA file of the open lot. */
async function showSepa(): Promise<void> {
  if (!detail.value) return;
  error.value = '';
  try {
    sepa.value = await apiGetText(`/api/ui/v1/payment/lots/${detail.value.id}/sepa`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Fichier SEPA indisponible';
  }
}

/**
 * Records the bank result of a payment order, then reloads the lot.
 * @param order the payment order
 * @param settled whether the order was settled
 */
async function recordResult(order: OrderView, settled: boolean): Promise<void> {
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

/** Creates an off-cycle lot for the entered distributor. */
async function createOffCycle(): Promise<void> {
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

/** Reloads the lots list while keeping the open lot open. */
async function loadLotsKeepingDetail(): Promise<void> {
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
    <PageHeading
      title="Paiement"
      subtitle="Lots de paiement d'une période, préparation, validation Finance et fichier SEPA, résultat bancaire et lot hors-cycle (sections 10.2 à 10.4)."
    />

    <FilterBar
      :loading="loading"
      search-label="Rafraîchir"
      search-testid="pay-refresh"
      :resettable="false"
      @search="loadLots"
    >
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="pay-entity" v-model="entity" />
      </FormField>
      <FormField label="Mois" stacked width="short" v-slot="{ id }">
        <MonthSelect :id="id" data-testid="pay-month" v-model="month" :entity="entity" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="pay-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="pay-message">{{ message }}</AppMessage>

    <AppCard v-if="canOperate()" title="Lot hors-cycle" data-testid="pay-offcycle-form">
      <AppForm @submit="createOffCycle">
        <FormField label="Code distributeur" width="short" v-slot="{ id }">
          <input :id="id" data-testid="pay-offcycle-partner" type="text" v-model="offCyclePartner" />
        </FormField>
        <AppButton type="submit" data-testid="pay-offcycle-submit">Créer le lot</AppButton>
      </AppForm>
    </AppCard>

    <AppCard flush>
      <DataTable
        :columns="lotColumns"
        :rows="lots"
        :loading="loading"
        row-key="id"
        empty="Aucun lot."
        empty-testid="no-lot"
        :row-testid="(l) => `lot-row-${l.id}`"
      >
        <template #cell-batchType="{ row }"><span :data-testid="`lot-type-${row.id}`">{{ row.batchType }}</span></template>
        <template #cell-status="{ row }">
          <span :data-testid="`lot-status-${row.id}`">{{ statusLabel(row.status) }}</span>
        </template>
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`lot-open-${row.id}`" @click="openLot(row.id)">Détail</AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="lot-detail">
      <h2 class="ui-detail-title">
        Lot {{ detail.batchType }} — {{ statusLabel(detail.status) }} — {{ amount(detail.total) }}
      </h2>
      <div class="ui-action-row">
        <AppButton v-if="canOperate() && detail.status === 'DRAFT'" variant="secondary" data-testid="lot-prepare" @click="prepare">
          Préparer
        </AppButton>
        <AppButton v-if="canFinance() && detail.status === 'DRAFT'" variant="secondary" data-testid="lot-validate" @click="validate">
          Valider (Finance)
        </AppButton>
        <AppButton v-if="canFinance() && detail.status === 'ISSUED' && detail.batchType !== 'CHEQUE'" variant="secondary" data-testid="lot-sepa" @click="showSepa">
          Fichier SEPA
        </AppButton>
        <AppButton
          v-if="canFinance() && detail.status === 'ISSUED' && detail.batchType === 'CHEQUE'"
          variant="secondary"
          data-testid="lot-cheque-emission"
          @click="chequeEmission"
        >
          Saisir l'émission des chèques
        </AppButton>
      </div>

      <h3 class="ui-detail-sub">Ordres</h3>
      <DataTable
        :columns="orderColumns"
        :rows="detail.orders"
        row-key="id"
        empty="Aucun ordre."
        empty-testid="no-order"
        :row-testid="(o) => `order-row-${o.id}`"
      >
        <template #cell-iban="{ row }"><span :data-testid="`order-iban-${row.id}`">{{ row.iban ?? '—' }}</span></template>
        <template #cell-status="{ row }">
          <span :data-testid="`order-status-${row.id}`">{{ statusLabel(row.status) }}</span>
        </template>
        <template #actions="{ row }">
          <template v-if="canFinance() && row.status === 'ISSUED'">
            <AppButton variant="secondary" :data-testid="`order-settle-${row.id}`" @click="recordResult(row, true)">Réglé</AppButton>
            <AppButton variant="danger" :data-testid="`order-reject-${row.id}`" @click="recordResult(row, false)">Rejeté</AppButton>
          </template>
        </template>
      </DataTable>

      <h3 class="ui-detail-sub">Exclusions</h3>
      <DataTable
        :columns="exclusionColumns"
        :rows="detail.exclusions"
        :row-key="(x) => detail?.exclusions.indexOf(x) ?? 0"
        empty="Aucune exclusion."
        empty-testid="no-exclusion"
        :row-testid="(x) => `exclusion-row-${detail?.exclusions.indexOf(x)}`"
      >
        <template #cell-reason="{ row }">
          <span :data-testid="`exclusion-reason-${detail?.exclusions.indexOf(row)}`">{{ exclusionReasonLabel(row.reason) }}</span>
        </template>
      </DataTable>

      <pre v-if="sepa" data-testid="sepa-content" class="ui-code">{{ sepa }}</pre>
    </AppCard>
  </div>
</template>

<style scoped>
.ui-detail-title {
  margin: 0;
  font-size: var(--fs-h2);
}
.ui-detail-sub {
  margin: 0;
  font-size: var(--fs-h3);
  font-weight: 600;
}
.ui-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}
.ui-code {
  margin: 0;
  overflow-x: auto;
  background: var(--line-soft);
  padding: var(--space-3);
  border-radius: var(--radius);
}
</style>
