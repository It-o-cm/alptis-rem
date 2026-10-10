<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';
import LineChart from '../components/charts/LineChart.vue';
import { AppButton, AppCard, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
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
const loading = ref(false);

const canOperate = (): boolean => canOperateCycle(session.roles);

const series = ref<MonthBalance[]>([]);

/** Columns of the distributor accounts table. */
const accountColumns: Column<AccountView>[] = [
  { key: 'account', label: 'Compte', value: (a) => `${a.payeeType} #${a.payeeId}` },
  { key: 'balance', label: 'Solde', align: 'num' },
  { key: 'blocked', label: 'Bloqué' },
];

/** Columns of the debtor accounts table. */
const debtorColumns: Column<AccountView>[] = [
  { key: 'account', label: 'Compte', value: (a) => `${a.payeeType} #${a.payeeId}` },
  { key: 'balance', label: 'Solde', align: 'num', value: (a) => amount(a.balance) },
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
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Distributeur introuvable';
  } finally {
    loading.value = false;
  }
}

/** The months shown on the balance chart. */
const balanceMonths = computed(() => series.value.map((p) => p.month));
/** The single balance series shown on the chart. */
const balanceSeries = computed(() => [{ key: 'balance', label: 'Solde', values: series.value.map((p) => p.balance) }]);

/** Loads the debtor accounts of the current entity. */
async function loadDebtors(): Promise<void> {
  debtors.value = await apiGet<AccountView[]>(`/api/ui/v1/accounts/${entity.value}/debtors`);
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
  await apiPost(`/api/ui/v1/accounts/${a.id}/block`, { reason: 'Blocage manuel' });
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
        <input :id="id" data-testid="acc-entity" type="text" v-model="entity" />
      </FormField>
      <FormField label="Mois" stacked width="date" v-slot="{ id }">
        <input :id="id" data-testid="acc-month" type="text" v-model="month" />
      </FormField>
      <FormField label="Code partenaire" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="acc-partner" type="text" v-model="partnerCode" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="acc-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="acc-message">{{ message }}</AppMessage>

    <AppCard v-if="view" data-testid="account-view">
      <h2 class="ui-detail-title">
        {{ view.partnerCode }} — solde total <span data-testid="account-total">{{ amount(view.total) }}</span>
      </h2>
      <div v-if="canOperate()">
        <AppButton data-testid="acc-compensate" @click="compensate">Compenser</AppButton>
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
  </div>
</template>

<style scoped>
.ui-detail-title {
  margin: 0;
  font-size: var(--fs-h2);
}
</style>
