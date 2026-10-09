<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle, paymentStatusLabel } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';

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
const spreadsheet = ref('');
const error = ref('');
const message = ref('');

const canOperate = () => canOperateCycle(session.roles);

function statusLabel(status: string): string {
  return paymentStatusLabel(status);
}

function amount(value: number | null): string {
  return value === null ? '—' : formatAmount(value);
}

async function loadStatements() {
  error.value = '';
  content.value = null;
  spreadsheet.value = '';
  try {
    statements.value = await apiGet<StatementView[]>(`/api/ui/v1/documents/${entity.value}/${month.value}/statements`);
  } catch (e) {
    statements.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function openStatement(id: number) {
  error.value = '';
  spreadsheet.value = '';
  try {
    content.value = await apiGet<StatementContent>(`/api/ui/v1/documents/statements/${id}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Bordereau introuvable';
  }
}

async function publish(s: StatementView) {
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

async function rectify(s: StatementView) {
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

async function showSpreadsheet(id: number) {
  error.value = '';
  try {
    spreadsheet.value = await apiGetText(`/api/ui/v1/documents/statements/${id}/spreadsheet`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Tableur indisponible';
  }
}

onMounted(loadStatements);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Bordereaux</h1>
      <div class="page__subtitle">
        Bordereaux de commission d'une période : aperçu par rubrique, publication, export tableur et version
        rectificative (section 10.6).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="brd-entity" type="text" v-model="entity" /></label>
        <label>Mois <input data-testid="brd-month" type="text" v-model="month" /></label>
        <button class="btn" data-testid="brd-refresh" @click="loadStatements">Rafraîchir</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="brd-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="brd-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Numéro</th><th>Version</th><th>Net</th><th>Statut</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="statements.length === 0"><td colspan="5" data-testid="no-statement">Aucun bordereau.</td></tr>
          <tr v-for="s in statements" :key="s.id" :data-testid="`statement-row-${s.id}`">
            <td>{{ s.statementNumber }}</td>
            <td>v{{ s.versionNo }}</td>
            <td>{{ amount(s.netAmount) }}</td>
            <td :data-testid="`statement-status-${s.id}`">{{ statusLabel(s.status) }}</td>
            <td style="display: flex; gap: 6px; flex-wrap: wrap">
              <button class="btn" :data-testid="`statement-open-${s.id}`" @click="openStatement(s.id)">Aperçu</button>
              <button class="btn" :data-testid="`statement-csv-${s.id}`" @click="showSpreadsheet(s.id)">Tableur</button>
              <button v-if="canOperate() && s.status === 'DRAFT'" class="btn"
                      :data-testid="`statement-publish-${s.id}`" @click="publish(s)">Publier</button>
              <button v-if="canOperate() && s.status === 'PUBLISHED'" class="btn"
                      :data-testid="`statement-rectify-${s.id}`" @click="rectify(s)">Rectifier</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="content" class="card" style="padding: 18px 20px" data-testid="statement-detail">
      <h2 style="margin: 0 0 4px; font-size: 16px">
        Bordereau {{ content.statementNumber }} v{{ content.versionNo }}
      </h2>
      <div class="page__subtitle" style="margin-bottom: 12px">{{ content.beneficiary }} — {{ content.month }}</div>
      <table>
        <thead>
          <tr><th>Rubrique</th><th>Libellé</th><th>Assiette</th><th>Taux</th><th>Montant</th><th>Motif</th></tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in content.rows" :key="i" :data-testid="`statement-line-${i}`">
            <td :data-testid="`statement-line-section-${i}`">{{ r.section }}</td>
            <td>{{ r.label }}</td>
            <td>{{ amount(r.basis) }}</td>
            <td>{{ r.rate ?? '—' }}</td>
            <td>{{ amount(r.amount) }}</td>
            <td>{{ r.reason ?? '—' }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr><td colspan="4">Total période</td><td data-testid="statement-period-total">{{ amount(content.periodTotal) }}</td><td></td></tr>
          <tr><td colspan="4">Report</td><td>{{ amount(content.carried) }}</td><td></td></tr>
          <tr><td colspan="4"><strong>Net payé</strong></td><td data-testid="statement-net"><strong>{{ amount(content.net) }}</strong></td><td></td></tr>
        </tfoot>
      </table>
    </section>

    <section v-if="spreadsheet" class="card" style="padding: 18px 20px" data-testid="spreadsheet-detail">
      <h2 style="margin: 0 0 12px; font-size: 16px">Export tableur</h2>
      <pre data-testid="spreadsheet-content" style="overflow-x: auto; background: #f6f8fa; padding: 12px">{{ spreadsheet }}</pre>
    </section>
  </div>
</template>
