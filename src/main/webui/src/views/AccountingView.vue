<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiGetText, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canProduceExport } from '../presentation/reporting';
import { formatAmount } from '../presentation/format';
import { AppButton, AppCard, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

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
const loading = ref(false);
const extracting = ref(false);

/** Whether the current user may produce the accounting extract. */
const canExtract = () => canProduceExport(session.roles);

/** Columns of the accounting scheme table. */
const schemeColumns: Column<SchemeRow & { i: number }>[] = [
  { key: 'event', label: 'Événement' },
  { key: 'variant', label: 'Variante' },
  { key: 'debitRole', label: 'Débit (rôle)' },
  { key: 'creditRole', label: 'Crédit (rôle)' },
];

/** Columns of the account-mapping table. */
const mappingColumns: Column<MappingRow & { i: number }>[] = [
  { key: 'role', label: 'Rôle' },
  { key: 'accountNumber', label: 'Compte' },
  { key: 'journal', label: 'Journal' },
];

/** Columns of the monthly entries table. */
const entryColumns: Column<Entry>[] = [
  { key: 'accountingEvent', label: 'Événement' },
  { key: 'journal', label: 'Journal' },
  { key: 'entryDate', label: 'Date' },
  { key: 'debitAccount', label: 'Débit' },
  { key: 'creditAccount', label: 'Crédit' },
  { key: 'amount', label: 'Montant', align: 'num', value: (e) => amount(e.amount) },
  { key: 'piece', label: 'Pièce', value: (e) => e.lineId ?? e.endToEndId ?? '—' },
];

/** Scheme rows paired with their index, for the per-row test identifier. */
const schemeRows = computed(() => (scheme.value ? scheme.value.schema.map((s, i) => ({ ...s, i })) : []));

/** Mapping rows paired with their index, for the per-row test identifier. */
const mappingRows = computed(() => (scheme.value ? scheme.value.mapping.map((m, i) => ({ ...m, i })) : []));

/**
 * Formats an amount with the common currency formatting.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Loads the accounting scheme and account mapping of the entity. */
async function loadScheme() {
  error.value = '';
  try {
    scheme.value = await apiGet<Scheme>(`/api/ui/v1/accounting/${entity.value}/scheme`);
  } catch (e) {
    scheme.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

/** Loads the accounting entries of the selected month. */
async function searchEntries() {
  error.value = '';
  try {
    entries.value = await apiGet<Entry[]>(`/api/ui/v1/accounting/${entity.value}/${month.value}/entries`);
  } catch (e) {
    entries.value = [];
    error.value = e instanceof ApiError ? e.message : 'Recherche impossible';
  }
}

/** Reloads the scheme and the entries for the current entity and month. */
async function refresh() {
  loading.value = true;
  try {
    await Promise.all([loadScheme(), searchEntries()]);
  } finally {
    loading.value = false;
  }
}

/** Produces the accounting extract and closes the month. */
async function extract() {
  message.value = '';
  error.value = '';
  extracting.value = true;
  try {
    const summary = await apiPost<{ fileRef: string }>(
      `/api/ui/v1/accounting/${entity.value}/${month.value}/extract`,
      {},
    );
    message.value = `Extraction produite : ${summary.fileRef}`;
    extractCsv.value = await apiGetText(`/api/ui/v1/accounting/${entity.value}/${month.value}/extract.csv`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Extraction refusée';
  } finally {
    extracting.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Comptabilité"
      subtitle="Schéma comptable et correspondance des comptes de l'entité, recherche des écritures du mois et extraction comptable à la clôture (section 10, FIN-01 à FIN-07)."
    />

    <FilterBar :loading="loading" search-label="Rafraîchir" search-testid="acc-refresh" :resettable="false" @search="refresh">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="acc-entity" type="text" v-model="entity" />
      </FormField>
      <FormField label="Mois" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="acc-month" type="text" v-model="month" />
      </FormField>
    </FilterBar>

    <div v-if="canExtract()" class="acc-actions">
      <AppButton variant="danger" :loading="extracting" data-testid="acc-extract" @click="extract">
        Extraire et clôturer
      </AppButton>
    </div>

    <AppMessage v-if="error" variant="error" data-testid="acc-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="acc-message">{{ message }}</AppMessage>

    <AppCard v-if="scheme" data-testid="acc-scheme">
      <h2 class="acc-subtitle">Schéma comptable</h2>
      <DataTable
        :columns="schemeColumns"
        :rows="schemeRows"
        empty="Aucun schéma."
        :row-testid="(s) => `scheme-row-${s.i}`"
      />
      <h2 class="acc-subtitle">Correspondance des comptes</h2>
      <DataTable
        :columns="mappingColumns"
        :rows="mappingRows"
        empty="Aucune correspondance."
        :row-testid="(m) => `mapping-row-${m.i}`"
      />
    </AppCard>

    <AppCard title="Écritures du mois">
      <DataTable
        :columns="entryColumns"
        :rows="entries"
        row-key="id"
        empty="Aucune écriture."
        empty-testid="no-entry"
        :row-testid="(e) => `entry-row-${e.id}`"
      >
        <template #cell-accountingEvent="{ row }">
          <span :data-testid="`entry-event-${row.id}`">{{ row.accountingEvent }}</span>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="extractCsv" data-testid="acc-extract-card" title="Extraction comptable (OUT-02)">
      <pre data-testid="acc-extract-csv" class="acc-csv">{{ extractCsv }}</pre>
    </AppCard>
  </div>
</template>

<style scoped>
.acc-actions {
  display: flex;
}
.acc-subtitle {
  margin: 0;
  font-size: var(--fs-h2);
  font-weight: 600;
}
.acc-csv {
  margin: 0;
  overflow-x: auto;
  background: var(--line-soft);
  padding: var(--space-3);
  border-radius: var(--radius);
}
</style>
