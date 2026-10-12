<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppCard, AppMessage, DataTable, DetailList, DetailRow, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface DistributorView {
  partnerCode: string;
  legalName: string;
  distributorType: string | null;
  groupCode: string | null;
}

interface StatusView {
  entityCode: string;
  status: string;
  validFrom: string | null;
  validTo: string | null;
}

interface ProductionCodeView {
  entityCode: string;
  code: string;
  label: string | null;
  status: string | null;
}

interface AgreementView {
  entityCode: string;
  agreementType: string;
  reference: string | null;
  validFrom: string | null;
  validTo: string | null;
  signatureStatus: string | null;
}

interface EnrollmentView {
  entityCode: string;
  ruleCode: string;
  ruleLabel: string;
  status: string | null;
  validFrom: string | null;
  validTo: string | null;
}

interface IndicatorView {
  entityCode: string;
  partnerCode: string | null;
  indicatorType: string | null;
  range: string | null;
  periodTo: string | null;
  value: string | null;
}

interface MonthBalanceView {
  month: string;
  balance: string;
}

interface AccountSummaryView {
  entityCode: string;
  balance: string;
  history: MonthBalanceView[];
}

interface DistributorDetail {
  partnerCode: string;
  legalName: string;
  groupCode: string | null;
  groupLabel: string | null;
  distributorType: string | null;
  statuses: StatusView[];
  productionCodes: ProductionCodeView[];
  agreements: AgreementView[];
  enrollments: EnrollmentView[];
  indicators: IndicatorView[];
  accounts: AccountSummaryView[];
}

const term = ref('');
const results = ref<DistributorView[]>([]);
const detail = ref<DistributorDetail | null>(null);
const error = ref('');
const loading = ref(false);

/** Columns of the distributor results table. */
const columns: Column<DistributorView>[] = [
  { key: 'partnerCode', label: 'Code partenaire' },
  { key: 'legalName', label: 'Raison sociale' },
  { key: 'distributorType', label: 'Type', value: (d) => d.distributorType ?? '—' },
  { key: 'groupCode', label: 'Groupement', value: (d) => d.groupCode ?? '—' },
];

/** Columns of the per-entity status table. */
const statusColumns: Column<StatusView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'status', label: 'Statut' },
  { key: 'validFrom', label: 'Depuis', value: (s) => s.validFrom ?? '—' },
  { key: 'validTo', label: "Jusqu'à", value: (s) => s.validTo ?? '—' },
];

/** Columns of the production-codes table. */
const codeColumns: Column<ProductionCodeView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé', value: (c) => c.label ?? '—' },
  { key: 'status', label: 'Statut', value: (c) => c.status ?? '—' },
];

/** Columns of the agreements table. */
const agreementColumns: Column<AgreementView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'agreementType', label: 'Nature' },
  { key: 'reference', label: 'Référence', value: (a) => a.reference ?? '—' },
  { key: 'signatureStatus', label: 'Signature', value: (a) => a.signatureStatus ?? '—' },
  { key: 'validFrom', label: 'Depuis', value: (a) => a.validFrom ?? '—' },
  { key: 'validTo', label: "Jusqu'à", value: (a) => a.validTo ?? '—' },
];

/** Columns of the enrollments table. */
const enrollmentColumns: Column<EnrollmentView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'ruleCode', label: 'Règle' },
  { key: 'ruleLabel', label: 'Libellé' },
  { key: 'status', label: 'Statut', value: (e) => e.status ?? '—' },
  { key: 'validFrom', label: 'Depuis', value: (e) => e.validFrom ?? '—' },
  { key: 'validTo', label: "Jusqu'à", value: (e) => e.validTo ?? '—' },
];

/** Columns of the quality-indicators table. */
const indicatorColumns: Column<IndicatorView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'indicatorType', label: 'Indicateur', value: (i) => i.indicatorType ?? '—' },
  { key: 'range', label: 'Gamme', value: (i) => i.range ?? '—' },
  { key: 'periodTo', label: 'Au', value: (i) => i.periodTo ?? '—' },
  { key: 'value', label: 'Valeur', value: (i) => i.value ?? '—' },
];

/** Columns of the account balance history table. */
const balanceColumns: Column<MonthBalanceView>[] = [
  { key: 'month', label: 'Mois' },
  { key: 'balance', label: 'Solde' },
];

/** Searches distributors by code or legal name within the user's entities. */
async function search(): Promise<void> {
  error.value = '';
  detail.value = null;
  loading.value = true;
  try {
    const page = await apiGet<{ items: DistributorView[] }>(
      `/api/ui/v1/distributors?q=${encodeURIComponent(term.value)}`,
    );
    results.value = page.items;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de recherche';
  } finally {
    loading.value = false;
  }
}

/** Clears the search term and its results. */
function reset(): void {
  term.value = '';
  results.value = [];
  detail.value = null;
  error.value = '';
}

/**
 * Loads the full detail of a distributor.
 * @param distributor the selected row
 */
async function open(distributor: DistributorView): Promise<void> {
  detail.value = await apiGet<DistributorDetail>(`/api/ui/v1/distributors/${distributor.partnerCode}`);
}

onMounted(search);
</script>

<template>
  <div class="page">
    <PageHeading title="Recherche distributeurs" subtitle="Par code partenaire ou raison sociale, dans vos entités." />

    <FilterBar :loading="loading" search-testid="search-button" @search="search" @reset="reset">
      <FormField label="Recherche" stacked width="long" v-slot="{ id }">
        <input :id="id" data-testid="search-term" type="text" v-model="term" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="search-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="results"
        :loading="loading"
        row-key="partnerCode"
        empty="Aucun distributeur."
        empty-testid="empty"
        clickable
        :row-testid="(d) => `distributor-row-${d.partnerCode}`"
        @row-click="open"
      >
        <template #cell-partnerCode="{ row }"><span class="mono">{{ row.partnerCode }}</span></template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="distributor-detail">
      <h2 class="ui-detail-title">{{ detail.legalName }} · {{ detail.partnerCode }}</h2>
      <DetailList>
        <DetailRow label="Type">{{ detail.distributorType ?? '—' }}</DetailRow>
        <DetailRow label="Groupement">
          {{ detail.groupCode ? `${detail.groupCode}${detail.groupLabel ? ' · ' + detail.groupLabel : ''}` : '—' }}
        </DetailRow>
      </DetailList>

      <h3 class="ui-detail-sub">Statut par entité</h3>
      <DataTable :columns="statusColumns" :rows="detail.statuses" empty="Aucun statut." :row-testid="() => 'status-row'" />

      <h3 class="ui-detail-sub">Codes de production</h3>
      <DataTable
        :columns="codeColumns"
        :rows="detail.productionCodes"
        empty="Aucun code de production."
        empty-testid="no-codes"
        :row-testid="(c) => `code-row-${c.code}`"
      >
        <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
      </DataTable>

      <h3 class="ui-detail-sub">Accords</h3>
      <DataTable
        :columns="agreementColumns"
        :rows="detail.agreements"
        empty="Aucun accord."
        empty-testid="no-agreements"
        :row-testid="(a) => `agreement-row-${a.reference ?? a.agreementType}`"
      />

      <h3 class="ui-detail-sub">Inscriptions aux règles réservées</h3>
      <DataTable
        :columns="enrollmentColumns"
        :rows="detail.enrollments"
        empty="Aucune inscription."
        empty-testid="no-enrollments"
        :row-testid="(e) => `enrollment-row-${e.ruleCode}`"
      >
        <template #cell-ruleCode="{ row }"><span class="mono">{{ row.ruleCode }}</span></template>
      </DataTable>

      <h3 class="ui-detail-sub">Indicateurs de qualité</h3>
      <DataTable
        :columns="indicatorColumns"
        :rows="detail.indicators"
        empty="Aucun indicateur."
        empty-testid="no-indicators"
        :row-testid="() => 'indicator-row'"
      />

      <h3 class="ui-detail-sub">Comptes et historique des soldes</h3>
      <AppMessage v-if="detail.accounts.length === 0" variant="info" data-testid="no-accounts">
        Aucun compte.
      </AppMessage>
      <div v-for="account in detail.accounts" :key="account.entityCode" :data-testid="`account-${account.entityCode}`">
        <DetailList>
          <DetailRow label="Entité">{{ account.entityCode }}</DetailRow>
          <DetailRow label="Solde">{{ account.balance }}</DetailRow>
        </DetailList>
        <DataTable
          :columns="balanceColumns"
          :rows="account.history"
          empty="Aucun mouvement."
          :row-testid="() => `balance-row-${account.entityCode}`"
        />
      </div>
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
</style>
