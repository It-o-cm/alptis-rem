<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateImports } from '../presentation/access';
import { flowLabel, importStatusLabel } from '../presentation/imports';
import { formatDate } from '../presentation/format';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface BatchView {
  id: number;
  flowCode: string;
  sourceSystem: string;
  fileName: string;
  fileHash: string;
  receivedAt: string;
  importedBy: string;
  linesRead: number | null;
  linesFiltered: number | null;
  linesLoaded: number | null;
  linesRejected: number | null;
  linesOnHold: number | null;
  status: string;
}

interface RejectionView {
  id: number;
  lineNumber: number;
  rawLine: string;
  reasonCode: string;
  status: string;
}

interface HeldView {
  sourceKey: string;
  contractNumber: string | null;
  holdReason: string | null;
}

interface ExpectedFlow {
  code: string;
  label: string;
}

interface DetailRowView {
  key: string;
  testid: string;
  kind: 'rejection' | 'held';
  line: string | number;
  motif: string;
  code: string;
  codeClass: string;
  status: string;
  rejection: RejectionView | null;
}

const FLOWS = [
  'events', 'coverages', 'distributors', 'distributor_groups', 'production_codes',
  'insurers', 'product_ranges', 'products', 'product_options', 'bank_accounts', 'quality_indicators',
  'portfolio_transfers', 'replacement_orders', 'payee_rules', 'external_codes',
];
const EXPECTED: ExpectedFlow[] = [
  { code: 'coverages', label: 'Garanties' },
  { code: 'events', label: 'Événements' },
  { code: 'distributors', label: 'Distributeurs' },
];

const session = useSessionStore();
const batches = ref<BatchView[]>([]);
const loadError = ref('');
const loading = ref(false);
const selected = ref<BatchView | null>(null);
const rejections = ref<RejectionView[]>([]);
const held = ref<HeldView[]>([]);
const message = ref('');
const uploadError = ref('');
/** The corrected line being edited per rejection (INT-03), prefilled with its raw line. */
const correctedLines = ref<Record<number, string>>({});
/** The error shown when a corrected line is still rejected. */
const correctionError = ref('');

const form = ref({ flowCode: 'distributors', sourceSystem: '', fileName: '', content: '' });
const sourceSystems = ref<string[]>([]);

/** Whether the current user may operate imports (upload, correct, discard). */
const canOperate = (): boolean => canOperateImports(session.roles);
/** The user's first entity, or an empty string when none. */
const entityCode = (): string => (session.entities.length > 0 ? session.entities[0] : '');

/** The set of flow codes already received, to flag expected flows. */
const receivedFlows = computed(() => new Set(batches.value.map((b) => b.flowCode)));

/** Columns of the imports journal. */
const journalColumns: Column<BatchView>[] = [
  { key: 'flowCode', label: 'Flux', value: (b) => flowLabel(b.flowCode) },
  { key: 'fileName', label: 'Fichier' },
  { key: 'sourceSystem', label: 'Système source' },
  { key: 'receivedAt', label: 'Reçu le', value: (b) => formatDate(b.receivedAt) },
  { key: 'linesRead', label: 'Lues', align: 'num' },
  { key: 'linesFiltered', label: 'Filtrées', align: 'num' },
  { key: 'linesLoaded', label: 'Intégrées', align: 'num' },
  { key: 'linesRejected', label: 'Rejetées', align: 'num' },
  { key: 'linesOnHold', label: 'En attente', align: 'num' },
  { key: 'status', label: 'État' },
];

/** Columns of the lines-to-process table of a selected batch. */
const detailColumns: Column<DetailRowView>[] = [
  { key: 'line', label: 'Ligne', align: 'num' },
  { key: 'motif', label: 'Motif' },
  { key: 'code', label: 'Code' },
  { key: 'status', label: 'Statut' },
  { key: 'action', label: 'Action' },
];

/** Columns of the expected-flows table. */
const expectedColumns: Column<ExpectedFlow>[] = [
  { key: 'label', label: 'Flux' },
  { key: 'situation', label: 'Situation' },
];

/** The rejected and on-hold lines of the selected batch, as display rows. */
const detailRows = computed<DetailRowView[]>(() => [
  ...rejections.value.map((r) => ({
    key: `r-${r.id}`,
    testid: `rejection-row-${r.id}`,
    kind: 'rejection' as const,
    line: r.lineNumber,
    motif: r.rawLine,
    code: r.reasonCode,
    codeClass: 'tag ko',
    status: r.status,
    rejection: r,
  })),
  ...held.value.map((h, i) => ({
    key: `h-${i}`,
    testid: `held-row-${i}`,
    kind: 'held' as const,
    line: '—',
    motif: `${h.sourceKey} · ${h.contractNumber ?? '—'}`,
    code: h.holdReason ?? '',
    codeClass: 'tag info',
    status: 'ON_HOLD',
    rejection: null,
  })),
]);

/** Loads the imports journal of the user's entities. */
async function loadJournal(): Promise<void> {
  loadError.value = '';
  loading.value = true;
  try {
    const page = await apiGet<{ items: BatchView[] }>('/api/ui/v1/imports');
    batches.value = page.items;
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

/**
 * Selects a batch and loads its rejected and on-hold lines.
 * @param batch the batch to open
 */
async function selectBatch(batch: BatchView): Promise<void> {
  selected.value = batch;
  rejections.value = await apiGet<RejectionView[]>(`/api/ui/v1/imports/${batch.id}/rejections`);
  for (const r of rejections.value) {
    if (correctedLines.value[r.id] === undefined) {
      correctedLines.value[r.id] = r.rawLine;
    }
  }
  held.value = await apiGet<HeldView[]>(`/api/ui/v1/imports/${batch.id}/held`);
}

/**
 * Reads the chosen file into the upload form.
 * @param event the file input change event
 */
async function onFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files && input.files[0];
  if (file) {
    form.value.fileName = file.name;
    form.value.content = await file.text();
  }
}

/** Uploads the file described by the form and refreshes the journal. */
async function submitUpload(): Promise<void> {
  message.value = '';
  uploadError.value = '';
  try {
    const result = await apiPost<{ linesRead: number; linesFiltered: number; linesLoaded: number; linesRejected: number; linesOnHold: number }>(
      '/api/ui/v1/imports',
      {
        entityCode: entityCode(),
        flowCode: form.value.flowCode,
        sourceSystem: form.value.sourceSystem,
        fileName: form.value.fileName,
        content: form.value.content,
      },
    );
    message.value = `Importé : ${result.linesLoaded} intégrée(s), ${result.linesRejected} rejetée(s), `
      + `${result.linesOnHold} en attente, ${result.linesFiltered} filtrée(s).`;
    await loadJournal();
  } catch (e) {
    uploadError.value = e instanceof ApiError ? e.message : "Échec de l'import";
  }
}

/**
 * Corrects and replays a rejected line, then refreshes.
 * @param rejection the rejected line
 */
async function correct(rejection: RejectionView): Promise<void> {
  const line = correctedLines.value[rejection.id] ?? '';
  try {
    await apiPost(
      `/api/ui/v1/imports/rejections/${rejection.id}/correct?correctedLine=${encodeURIComponent(line)}`, {});
    correctionError.value = '';
    await refreshSelected();
  } catch (e) {
    correctionError.value = e instanceof ApiError ? e.message : 'Correction impossible';
  }
}

/**
 * Discards a rejected line, then refreshes.
 * @param rejection the rejected line
 */
async function discard(rejection: RejectionView): Promise<void> {
  await apiPost(`/api/ui/v1/imports/rejections/${rejection.id}/discard`, {});
  await refreshSelected();
}

/** Reloads the selected batch and the journal after a line action. */
async function refreshSelected(): Promise<void> {
  if (selected.value) {
    await selectBatch(selected.value);
    await loadJournal();
  }
}

/**
 * Tag class matching a batch status.
 * @param status the batch status
 */
function stateClass(status: string): string {
  if (status === 'LOADED') return 'tag ok';
  if (status === 'PARTIALLY_LOADED') return 'tag warn';
  if (status === 'REFUSED') return 'tag ko';
  return 'tag mute';
}

/** Loads the source systems the upload form may choose from, selecting the first. */
async function loadSourceSystems(): Promise<void> {
  if (!canOperate()) return;
  sourceSystems.value = await apiGet<string[]>('/api/ui/v1/imports/source-systems');
  if (sourceSystems.value.length > 0) {
    form.value.sourceSystem = sourceSystems.value[0];
  }
}

onMounted(async () => {
  await loadJournal();
  await loadSourceSystems();
});
</script>

<template>
  <div class="page">
    <PageHeading
      title="Imports et rejets"
      subtitle="Chaque fichier est contrôlé ligne par ligne : une ligne fausse est rejetée, une ligne juste mais non traitable est intégrée en attente. Un fichier déjà intégré est refusé."
    />

    <AppCard v-if="canOperate()" title="Déposer un fichier" data-testid="upload-form">
      <AppForm @submit="submitUpload">
        <FormField label="Flux" width="short" v-slot="{ id }">
          <select :id="id" data-testid="upload-flow" v-model="form.flowCode">
            <option v-for="f in FLOWS" :key="f" :value="f">{{ flowLabel(f) }}</option>
          </select>
        </FormField>
        <FormField label="Système source" width="short" v-slot="{ id }">
          <select :id="id" data-testid="upload-source" v-model="form.sourceSystem">
            <option v-for="s in sourceSystems" :key="s" :value="s">{{ s }}</option>
          </select>
        </FormField>
        <FormField label="Fichier" width="long" v-slot="{ id }">
          <input :id="id" data-testid="upload-file" type="file" @change="onFile" />
        </FormField>
        <AppButton type="submit" data-testid="upload-submit">Importer</AppButton>
      </AppForm>
      <AppMessage v-if="message" variant="success" data-testid="upload-message">{{ message }}</AppMessage>
      <AppMessage v-if="uploadError" variant="error" data-testid="upload-error">{{ uploadError }}</AppMessage>
    </AppCard>

    <AppMessage v-if="loadError" variant="error" data-testid="load-error">{{ loadError }}</AppMessage>

    <AppCard v-else title="Journal des imports">
      <DataTable
        :columns="journalColumns"
        :rows="batches"
        :loading="loading"
        row-key="id"
        empty="Aucun import."
        empty-testid="empty"
        clickable
        :row-testid="(b) => `batch-row-${b.id}`"
        @row-click="selectBatch"
      >
        <template #cell-fileName="{ row }"><span class="mono">{{ row.fileName }}</span></template>
        <template #cell-status="{ row }">
          <span :class="stateClass(row.status)" data-testid="batch-state">{{ importStatusLabel(row.status) }}</span>
        </template>
      </DataTable>
      <p class="page__subtitle">Lues = filtrées + intégrées + rejetées + en attente.</p>
    </AppCard>

    <AppCard v-if="selected" data-testid="batch-detail" :title="`Lignes à traiter · ${selected.fileName}`">
      <p class="page__subtitle">
        Empreinte <span class="mono" data-testid="batch-fingerprint">{{ selected.fileHash }}</span> ·
        déposé par <span data-testid="batch-author">{{ selected.importedBy }}</span>
      </p>
      <AppMessage v-if="correctionError" variant="error" data-testid="correction-error">{{ correctionError }}</AppMessage>
      <DataTable
        :columns="detailColumns"
        :rows="detailRows"
        row-key="key"
        empty="Aucune ligne en anomalie."
        empty-testid="detail-empty"
        :row-testid="(r) => r.testid"
      >
        <template #cell-motif="{ row }"><span class="mono">{{ row.motif }}</span></template>
        <template #cell-code="{ row }"><span :class="row.codeClass" :data-testid="`detail-code-${row.key}`">{{ row.code }}</span></template>
        <template #cell-action="{ row }">
          <template v-if="row.kind === 'rejection' && canOperate() && row.status === 'OPEN' && row.rejection">
            <input
              class="ui-correct-line mono"
              :data-testid="`correct-line-${row.rejection.id}`"
              v-model="correctedLines[row.rejection.id]"
              aria-label="Ligne corrigée"
            />
            <AppButton variant="secondary" :data-testid="`correct-${row.rejection.id}`" @click="correct(row.rejection)">
              Corriger et relancer
            </AppButton>
            <AppButton variant="secondary" :data-testid="`discard-${row.rejection.id}`" @click="discard(row.rejection)">
              Écarter
            </AppButton>
          </template>
          <span v-else-if="row.kind === 'held'" class="ui-hint">Repart seule à l'arrivée de la donnée manquante</span>
        </template>
      </DataTable>
    </AppCard>

    <AppCard title="Flux attendus sur la période">
      <DataTable
        :columns="expectedColumns"
        :rows="EXPECTED"
        row-key="code"
        :row-testid="(f) => `flow-${f.code}`"
      >
        <template #cell-situation="{ row }">
          <span v-if="receivedFlows.has(row.code)" class="tag ok">Reçu</span>
          <span v-else class="tag warn">Manquant</span>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.ui-hint {
  font-size: var(--fs-small);
  color: var(--muted);
}
.ui-correct-line {
  width: 100%;
  margin-bottom: var(--sp-1);
}
</style>
