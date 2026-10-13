<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';
import LineChart from '../components/charts/LineChart.vue';
import { AppButton, AppCard, AppMessage, DataTable, DistributorSelect, EntitySelect, FilterBar, FormField, MonthSelect, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface MonthBalance {
  month: string;
  balance: number;
}

interface AccountView {
  id: number;
  payeeType: string;
  payeeId: number;
  balance: number;
  blocked: boolean;
  blockReason: string | null;
  blockedUntil: string | null;
}

interface LineView {
  id: number;
  nature: string | null;
  amount: number;
  status: string | null;
  attachmentMonth: string | null;
  blocked: boolean;
  blockReason: string | null;
  blockedUntil: string | null;
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
interface RepaymentScheduleView {
  holder: string;
  amount: number;
  schedule: string;
  status: string | null;
}

const view = ref<DistributorView | null>(null);
const lines = ref<LineView[]>([]);
const debtors = ref<AccountView[]>([]);
const schedules = ref<RepaymentScheduleView[]>([]);
const blockReason = ref('Blocage manuel');
const blockUntil = ref('');
const error = ref('');
const message = ref('');
const loading = ref(false);

const canOperate = (): boolean => canOperateCycle(session.roles);

const series = ref<MonthBalance[]>([]);

/** Columns of the distributor accounts table. */
const accountColumns: Column<AccountView>[] = [
  { key: 'account', label: 'Compte', value: (a) => `${a.payeeType} #${a.payeeId}` },
  { key: 'balance', label: 'Solde', align: 'num' },
  { key: 'blocked', label: 'Bloqué' },
  { key: 'blockReason', label: 'Motif', value: (a) => a.blockReason ?? '—' },
  { key: 'blockedUntil', label: 'Levée le', value: (a) => a.blockedUntil ?? '—' },
];

/** Columns of the commission-lines table. */
const lineColumns: Column<LineView>[] = [
  { key: 'attachmentMonth', label: 'Mois', value: (l) => l.attachmentMonth ?? '—' },
  { key: 'nature', label: 'Nature', value: (l) => l.nature ?? '—' },
  { key: 'amount', label: 'Montant', align: 'num', value: (l) => amount(l.amount) },
  { key: 'status', label: 'Statut', value: (l) => l.status ?? '—' },
  { key: 'blocked', label: 'Bloquée', value: (l) => (l.blocked ? 'Oui' : 'Non') },
  { key: 'blockReason', label: 'Motif', value: (l) => l.blockReason ?? '—' },
  { key: 'blockedUntil', label: 'Levée le', value: (l) => l.blockedUntil ?? '—' },
];

/** Columns of the debtor accounts table. */
const debtorColumns: Column<AccountView>[] = [
  { key: 'account', label: 'Compte', value: (a) => `${a.payeeType} #${a.payeeId}` },
  { key: 'balance', label: 'Solde', align: 'num', value: (a) => amount(a.balance) },
];

/**
 * Renders a repayment schedule stored as JSON: a JSON string shows its text, anything else its raw
 * JSON.
 * @param raw the stored JSON schedule
 */
function scheduleText(raw: string): string {
  try {
    const parsed: unknown = JSON.parse(raw);
    return typeof parsed === 'string' ? parsed : raw;
  } catch {
    return raw;
  }
}

/** Columns of the apurement (repayment) schedules table. */
const scheduleColumns: Column<RepaymentScheduleView>[] = [
  { key: 'holder', label: 'Distributeur' },
  { key: 'amount', label: 'Montant', align: 'num', value: (s) => amount(s.amount) },
  { key: 'schedule', label: 'Échéancier', value: (s) => scheduleText(s.schedule) },
  { key: 'status', label: 'Statut', value: (s) => s.status ?? '—' },
];

/** Loads a distributor's accounts and its balance series for the current filter. */
async function search(): Promise<void> {
  error.value = '';
  view.value = null;
  series.value = [];
  loading.value = true;
  try {
    view.value = await apiGet<DistributorView>(`/api/ui/v1/accounts/${entity.value}/distributor/${partnerCode.value}`);
    series.value = await apiGet<MonthBalance[]>(
      `/api/ui/v1/accounts/${entity.value}/distributor/${partnerCode.value}/balance-series`);
    lines.value = await apiGet<LineView[]>(
      `/api/ui/v1/accounts/${entity.value}/distributor/${partnerCode.value}/lines`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Distributeur introuvable';
  } finally {
    loading.value = false;
  }
}

/** The block request body from the entered reason and optional lifting date. */
function blockBody(): { reason: string; until: string | null } {
  return { reason: blockReason.value, until: blockUntil.value === '' ? null : blockUntil.value };
}

/** The months shown on the balance chart. */
const balanceMonths = computed(() => series.value.map((p) => p.month));
/** The single balance series shown on the chart. */
const balanceSeries = computed(() => [{ key: 'balance', label: 'Solde', values: series.value.map((p) => p.balance) }]);

/** Loads the debtor accounts and the apurement schedules of the current entity. */
async function loadDebtors(): Promise<void> {
  debtors.value = await apiGet<AccountView[]>(`/api/ui/v1/accounts/${entity.value}/debtors`);
  schedules.value = await apiGet<RepaymentScheduleView[]>(`/api/ui/v1/accounts/${entity.value}/repayment-schedules`);
}

/** Writes the compensation lines for the distributor in the selected month. */
async function compensate(): Promise<void> {
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

/**
 * Blocks an account and refreshes the view.
 * @param a the account to block
 */
async function block(a: AccountView): Promise<void> {
  await apiPost(`/api/ui/v1/accounts/${a.id}/block`, blockBody());
  await search();
}

/**
 * Unblocks an account and refreshes the view.
 * @param a the account to unblock
 */
async function unblock(a: AccountView): Promise<void> {
  await apiPost(`/api/ui/v1/accounts/${a.id}/unblock`, {});
  await search();
}

/**
 * Blocks a commission line and refreshes the view.
 * @param l the line to block
 */
async function blockLine(l: LineView): Promise<void> {
  await apiPost(`/api/ui/v1/accounts/lines/${l.id}/block`, blockBody());
  await search();
}

/**
 * Unblocks a commission line and refreshes the view.
 * @param l the line to unblock
 */
async function unblockLine(l: LineView): Promise<void> {
  await apiPost(`/api/ui/v1/accounts/lines/${l.id}/unblock`, {});
  await search();
}

/**
 * Formats an amount in euros.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

onMounted(loadDebtors);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Fiche distributeur et compte"
      subtitle="Les comptes d'un distributeur, leur solde, la compensation et le blocage (section 8.8)."
    />

    <FilterBar :loading="loading" :resettable="false" search-testid="acc-search" @search="search">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="acc-entity" v-model="entity" />
      </FormField>
      <FormField label="Mois" stacked width="date" v-slot="{ id }">
        <MonthSelect :id="id" data-testid="acc-month" v-model="month" :entity="entity" value-format="date" />
      </FormField>
      <FormField label="Distributeur" stacked width="short" v-slot="{ id }">
        <DistributorSelect :id="id" data-testid="acc-partner" v-model="partnerCode" allow-empty />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="acc-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="acc-message">{{ message }}</AppMessage>

    <AppCard v-if="view" data-testid="account-view">
      <h2 class="ui-detail-title">
        {{ view.partnerCode }} — solde total <span data-testid="account-total">{{ amount(view.total) }}</span>
      </h2>
      <div v-if="canOperate()" class="ui-block-params">
        <AppButton data-testid="acc-compensate" @click="compensate">Compenser</AppButton>
        <FormField label="Motif de blocage" stacked width="long" v-slot="{ id }">
          <input :id="id" data-testid="block-reason" type="text" v-model="blockReason" />
        </FormField>
        <FormField label="Date de levée" stacked width="date" v-slot="{ id }">
          <input :id="id" data-testid="block-until" type="text" v-model="blockUntil" placeholder="AAAA-MM-JJ" />
        </FormField>
      </div>
      <DataTable
        :columns="accountColumns"
        :rows="view.accounts"
        row-key="id"
        :row-testid="(a) => `account-row-${a.id}`"
      >
        <template #cell-balance="{ row }">
          <span :data-testid="`account-balance-${row.id}`">{{ amount(row.balance) }}</span>
        </template>
        <template #cell-blocked="{ row }">
          <span :data-testid="`account-blocked-${row.id}`">{{ row.blocked ? 'Oui' : 'Non' }}</span>
        </template>
        <template #actions="{ row }">
          <template v-if="canOperate()">
            <AppButton v-if="!row.blocked" variant="secondary" :data-testid="`account-block-${row.id}`" @click="block(row)">
              Bloquer
            </AppButton>
            <AppButton v-else variant="secondary" :data-testid="`account-unblock-${row.id}`" @click="unblock(row)">
              Débloquer
            </AppButton>
          </template>
        </template>
      </DataTable>

      <h3 class="ui-detail-sub">Lignes de commission</h3>
      <DataTable
        :columns="lineColumns"
        :rows="lines"
        row-key="id"
        empty="Aucune ligne de commission."
        empty-testid="no-lines"
        :row-testid="(l) => `line-row-${l.id}`"
      >
        <template #actions="{ row }">
          <template v-if="canOperate()">
            <AppButton v-if="!row.blocked" variant="secondary" :data-testid="`line-block-${row.id}`" @click="blockLine(row)">
              Bloquer
            </AppButton>
            <AppButton v-else variant="secondary" :data-testid="`line-unblock-${row.id}`" @click="unblockLine(row)">
              Débloquer
            </AppButton>
          </template>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="series.length > 0">
      <LineChart :categories="balanceMonths" :series="balanceSeries" title="Solde du compte dans le temps"
                 :caption="view ? view.partnerCode : ''" testid="chart-account-balance" />
    </AppCard>

    <AppCard title="Comptes débiteurs" data-testid="debtors">
      <DataTable
        :columns="debtorColumns"
        :rows="debtors"
        row-key="id"
        empty="Aucun compte débiteur."
        empty-testid="no-debtor"
        :row-testid="(a) => `debtor-row-${a.id}`"
      />
    </AppCard>

    <AppCard title="Échéanciers d'apurement" data-testid="schedules">
      <DataTable
        :columns="scheduleColumns"
        :rows="schedules"
        empty="Aucun échéancier d'apurement."
        empty-testid="no-schedule"
        :row-testid="() => 'schedule-row'"
      />
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
.ui-block-params {
  display: flex;
  gap: var(--sp-3);
  align-items: flex-end;
  flex-wrap: wrap;
}
</style>
