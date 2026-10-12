<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, apiPost, apiDownload, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle, paymentStatusLabel } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';
import {
  AppButton, AppCard, AppMessage, DataTable, DetailList, DetailRow, FilterBar, FormField, PageHeading,
} from '../components/ui';
import type { Column } from '../components/ui';

interface StatementView {
  id: number;
  statementNumber: string;
  versionNo: number;
  netAmount: number;
  status: string;
}

interface Row {
  section: string;
  label: string;
  basis: number | null;
  rate: number | null;
  amount: number;
  reason: string | null;
}

interface StatementContent {
  statementNumber: string;
  versionNo: number;
  beneficiary: string;
  month: string;
  rows: Row[];
  periodTotal: number;
  carried: number;
  net: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const month = ref('2026-09-01');
const statements = ref<StatementView[]>([]);
const content = ref<StatementContent | null>(null);
const memberCode = ref('');
const spreadsheet = ref('');
const error = ref('');
const message = ref('');
const loading = ref(false);

/** Whether the current user may operate the cycle (publish, rectify). */
const canOperate = (): boolean => canOperateCycle(session.roles);

/** Columns of the statements table. */
const statementColumns: Column<StatementView>[] = [
  { key: 'statementNumber', label: 'Numéro' },
  { key: 'versionNo', label: 'Version', value: (s) => `v${s.versionNo}` },
  { key: 'netAmount', label: 'Net', align: 'num', value: (s) => amount(s.netAmount) },
  { key: 'status', label: 'Statut' },
];

/** Columns of the statement detail table. */
const rowColumns: Column<Row>[] = [
  { key: 'section', label: 'Rubrique' },
  { key: 'label', label: 'Libellé' },
  { key: 'basis', label: 'Assiette', align: 'num', value: (r) => amount(r.basis) },
  { key: 'rate', label: 'Taux', align: 'num', value: (r) => r.rate ?? '—' },
  { key: 'amount', label: 'Montant', align: 'num', value: (r) => amount(r.amount) },
  { key: 'reason', label: 'Motif', value: (r) => r.reason ?? '—' },
];

/**
 * French label of a payment status.
 * @param status the status code
 */
function statusLabel(status: string): string {
  return paymentStatusLabel(status);
}

/**
 * Formats a monetary value in euros, or a dash when null.
 * @param value the amount or null
 */
function amount(value: number | null): string {
  return value === null ? '—' : formatAmount(value);
}

/** Loads the statements of the selected entity and month. */
async function loadStatements(): Promise<void> {
  error.value = '';
  content.value = null;
  spreadsheet.value = '';
  loading.value = true;
  try {
    statements.value = await apiGet<StatementView[]>(`/api/ui/v1/documents/${entity.value}/${month.value}/statements`);
  } catch (e) {
    statements.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/**
 * Opens a statement and loads its content.
 * @param id the statement id
 */
async function openStatement(id: number): Promise<void> {
  error.value = '';
  spreadsheet.value = '';
  try {
    content.value = await apiGet<StatementContent>(`/api/ui/v1/documents/statements/${id}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Bordereau introuvable';
  }
}

/** Loads the informative statement of a group member (BRD-11). */
async function loadMemberStatement(): Promise<void> {
  error.value = '';
  spreadsheet.value = '';
  try {
    content.value = await apiGet<StatementContent>(
      `/api/ui/v1/documents/${entity.value}/${month.value}/member/${memberCode.value}`,
    );
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Relevé introuvable';
  }
}

/**
 * Publishes a draft statement.
 * @param s the statement
 */
async function publish(s: StatementView): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/documents/statements/${s.id}/publish`, {});
    message.value = 'Bordereau publié.';
    await loadStatements();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Publication refusée';
  }
}

/**
 * Creates a rectifying version of a published statement.
 * @param s the statement
 */
async function rectify(s: StatementView): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/documents/statements/${s.id}/rectify`, {});
    message.value = 'Version rectificative créée.';
    await loadStatements();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Rectification refusée';
  }
}

/**
 * Loads the spreadsheet export of a statement.
 * @param id the statement id
 */
async function showSpreadsheet(id: number): Promise<void> {
  error.value = '';
  try {
    spreadsheet.value = await apiGetText(`/api/ui/v1/documents/statements/${id}/spreadsheet`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Tableur indisponible';
  }
}

/**
 * Downloads the real PDF of a statement (BRD-01).
 * @param id the statement id
 */
async function downloadPdf(id: number): Promise<void> {
  error.value = '';
  try {
    await apiDownload(`/api/ui/v1/documents/statements/${id}/pdf`, `bordereau-${id}.pdf`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'PDF indisponible';
  }
}

/**
 * Downloads the real spreadsheet (xlsx) of a statement (BRD-01).
 * @param id the statement id
 */
async function downloadWorkbook(id: number): Promise<void> {
  error.value = '';
  try {
    await apiDownload(`/api/ui/v1/documents/statements/${id}/workbook`, `bordereau-${id}.xlsx`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Classeur indisponible';
  }
}

onMounted(loadStatements);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Bordereaux"
      subtitle="Bordereaux de commission d'une période : aperçu par rubrique, publication, export tableur et version rectificative (section 10.6)."
    />

    <FilterBar
      :loading="loading"
      search-label="Rafraîchir"
      search-testid="brd-refresh"
      :resettable="false"
      @search="loadStatements"
    >
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="brd-entity" type="text" v-model="entity" />
      </FormField>
      <FormField label="Mois" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="brd-month" type="text" v-model="month" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="brd-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="brd-message">{{ message }}</AppMessage>

    <AppCard title="Relevé informatif d'un membre de groupement (BRD-11)" data-testid="member-statement-form">
      <div class="ui-bar">
        <FormField label="Code de production du membre" stacked width="short" v-slot="{ id }">
          <input :id="id" data-testid="brd-member" type="text" v-model="memberCode" />
        </FormField>
        <AppButton variant="secondary" data-testid="brd-member-btn" @click="loadMemberStatement">
          Relevé informatif
        </AppButton>
      </div>
    </AppCard>

    <AppCard flush>
      <DataTable
        :columns="statementColumns"
        :rows="statements"
        :loading="loading"
        row-key="id"
        empty="Aucun bordereau."
        empty-testid="no-statement"
        :row-testid="(s) => `statement-row-${s.id}`"
      >
        <template #cell-status="{ row }">
          <span :data-testid="`statement-status-${row.id}`">{{ statusLabel(row.status) }}</span>
        </template>
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`statement-open-${row.id}`" @click="openStatement(row.id)">Aperçu</AppButton>
          <AppButton variant="secondary" :data-testid="`statement-csv-${row.id}`" @click="showSpreadsheet(row.id)">Tableur</AppButton>
          <AppButton variant="secondary" :data-testid="`statement-pdf-${row.id}`" @click="downloadPdf(row.id)">PDF</AppButton>
          <AppButton variant="secondary" :data-testid="`statement-xlsx-${row.id}`" @click="downloadWorkbook(row.id)">Classeur</AppButton>
          <AppButton
            v-if="canOperate() && row.status === 'DRAFT'"
            variant="secondary"
            :data-testid="`statement-publish-${row.id}`"
            @click="publish(row)"
          >
            Publier
          </AppButton>
          <AppButton
            v-if="canOperate() && row.status === 'PUBLISHED'"
            variant="secondary"
            :data-testid="`statement-rectify-${row.id}`"
            @click="rectify(row)"
          >
            Rectifier
          </AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="content" data-testid="statement-detail">
      <h2 class="ui-detail-title">
        {{ content.statementNumber }}<span v-if="content.versionNo"> v{{ content.versionNo }}</span>
      </h2>
      <p class="page__subtitle">{{ content.beneficiary }} — {{ content.month }}</p>
      <DataTable
        :columns="rowColumns"
        :rows="content.rows"
        :row-key="(r) => content?.rows.indexOf(r) ?? 0"
        :row-testid="(r) => `statement-line-${content?.rows.indexOf(r)}`"
      >
        <template #cell-section="{ row }">
          <span :data-testid="`statement-line-section-${content?.rows.indexOf(row)}`">{{ row.section }}</span>
        </template>
      </DataTable>
      <DetailList>
        <DetailRow label="Total période">
          <span data-testid="statement-period-total">{{ amount(content.periodTotal) }}</span>
        </DetailRow>
        <DetailRow label="Report">{{ amount(content.carried) }}</DetailRow>
        <DetailRow label="Net payé">
          <strong data-testid="statement-net">{{ amount(content.net) }}</strong>
        </DetailRow>
      </DetailList>
    </AppCard>

    <AppCard v-if="spreadsheet" title="Export tableur" data-testid="spreadsheet-detail">
      <pre data-testid="spreadsheet-content" class="ui-code">{{ spreadsheet }}</pre>
    </AppCard>
  </div>
</template>

<style scoped>
.ui-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
}
.ui-detail-title {
  margin: 0;
  font-size: var(--fs-h2);
}
.ui-code {
  margin: 0;
  overflow-x: auto;
  background: var(--line-soft);
  padding: var(--space-3);
  border-radius: var(--radius);
}
</style>
