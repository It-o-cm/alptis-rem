<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canProduceExport } from '../presentation/reporting';
import { formatAmount } from '../presentation/format';

interface SchemeRow {
  event: string;
  variant: string;
  debitRole: string;
  creditRole: string;
}

interface MappingRow {
  role: string;
  accountNumber: string;
  journal: string;
}

interface Scheme {
  schema: SchemeRow[];
  mapping: MappingRow[];
}

interface Entry {
  id: number;
  accountingEvent: string;
  journal: string;
  entryDate: string;
  debitAccount: string;
  creditAccount: string;
  amount: number;
  lineId: number | null;
  endToEndId: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const month = ref('2026-09-01');
const scheme = ref<Scheme | null>(null);
const entries = ref<Entry[]>([]);
const extractCsv = ref('');
const error = ref('');
const message = ref('');

const canExtract = () => canProduceExport(session.roles);

function amount(value: number): string {
  return formatAmount(value);
}

async function loadScheme() {
  error.value = '';
  try {
    scheme.value = await apiGet<Scheme>(`/api/ui/v1/accounting/${entity.value}/scheme`);
  } catch (e) {
    scheme.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function searchEntries() {
  error.value = '';
  try {
    entries.value = await apiGet<Entry[]>(`/api/ui/v1/accounting/${entity.value}/${month.value}/entries`);
  } catch (e) {
    entries.value = [];
    error.value = e instanceof ApiError ? e.message : 'Recherche impossible';
  }
}

async function extract() {
  message.value = '';
  error.value = '';
  try {
    const summary = await apiPost<{ fileRef: string }>(
      `/api/ui/v1/accounting/${entity.value}/${month.value}/extract`,
      {},
    );
    message.value = `Extraction produite : ${summary.fileRef}`;
    extractCsv.value = await apiGetText(`/api/ui/v1/accounting/${entity.value}/${month.value}/extract.csv`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Extraction refusée';
  }
}

onMounted(() => {
  loadScheme();
  searchEntries();
});
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Comptabilité</h1>
      <div class="page__subtitle">
        Schéma comptable et correspondance des comptes de l'entité, recherche des écritures du mois et
        extraction comptable à la clôture (section 10, FIN-01 à FIN-07).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="acc-entity" type="text" v-model="entity" /></label>
        <label>Mois <input data-testid="acc-month" type="text" v-model="month" /></label>
        <button class="btn" data-testid="acc-refresh" @click="loadScheme(); searchEntries()">Rafraîchir</button>
        <button v-if="canExtract()" class="btn" data-testid="acc-extract" @click="extract">Extraire et clôturer</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="acc-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="acc-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <section v-if="scheme" class="card" style="overflow-x: auto" data-testid="acc-scheme">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Schéma comptable</h2>
      <table>
        <thead><tr><th>Événement</th><th>Variante</th><th>Débit (rôle)</th><th>Crédit (rôle)</th></tr></thead>
        <tbody>
          <tr v-for="(s, i) in scheme.schema" :key="i" :data-testid="`scheme-row-${i}`">
            <td>{{ s.event }}</td><td>{{ s.variant }}</td><td>{{ s.debitRole }}</td><td>{{ s.creditRole }}</td>
          </tr>
        </tbody>
      </table>
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Correspondance des comptes</h2>
      <table>
        <thead><tr><th>Rôle</th><th>Compte</th><th>Journal</th></tr></thead>
        <tbody>
          <tr v-for="(m, i) in scheme.mapping" :key="i" :data-testid="`mapping-row-${i}`">
            <td>{{ m.role }}</td><td>{{ m.accountNumber }}</td><td>{{ m.journal }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card" style="overflow-x: auto">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Écritures du mois</h2>
      <table>
        <thead>
          <tr><th>Événement</th><th>Journal</th><th>Date</th><th>Débit</th><th>Crédit</th><th>Montant</th><th>Pièce</th></tr>
        </thead>
        <tbody>
          <tr v-if="entries.length === 0"><td colspan="7" data-testid="no-entry">Aucune écriture.</td></tr>
          <tr v-for="e in entries" :key="e.id" :data-testid="`entry-row-${e.id}`">
            <td :data-testid="`entry-event-${e.id}`">{{ e.accountingEvent }}</td>
            <td>{{ e.journal }}</td>
            <td>{{ e.entryDate }}</td>
            <td>{{ e.debitAccount }}</td>
            <td>{{ e.creditAccount }}</td>
            <td>{{ amount(e.amount) }}</td>
            <td>{{ e.lineId ?? e.endToEndId ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="extractCsv" class="card" style="padding: 18px 20px" data-testid="acc-extract-card">
      <h2 style="margin: 0 0 12px; font-size: 16px">Extraction comptable (OUT-02)</h2>
      <pre data-testid="acc-extract-csv" style="overflow-x: auto; background: #f6f8fa; padding: 12px">{{ extractCsv }}</pre>
    </section>
  </div>
</template>
