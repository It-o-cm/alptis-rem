<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateImports } from '../presentation/access';
import { formatDate } from '../presentation/format';

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

const FLOWS = [
  'events', 'coverages', 'distributors', 'distributor_groups', 'production_codes',
  'insurers', 'product_ranges', 'products', 'product_options', 'bank_accounts', 'quality_indicators',
  'portfolio_transfers', 'replacement_orders', 'payee_rules', 'external_codes',
];
const EXPECTED = [
  { code: 'coverages', label: 'Garanties' },
  { code: 'events', label: 'Événements' },
  { code: 'distributors', label: 'Distributeurs' },
];

const session = useSessionStore();
const batches = ref<BatchView[]>([]);
const loadError = ref('');
const selected = ref<BatchView | null>(null);
const rejections = ref<RejectionView[]>([]);
const held = ref<HeldView[]>([]);
const message = ref('');
const uploadError = ref('');

const form = ref({ flowCode: 'distributors', sourceSystem: 'SESAME', fileName: '', content: '' });

const canOperate = () => canOperateImports(session.roles);
const entityCode = () => (session.entities.length > 0 ? session.entities[0] : '');

const receivedFlows = computed(() => new Set(batches.value.map((b) => b.flowCode)));

async function loadJournal() {
  loadError.value = '';
  try {
    const page = await apiGet<{ items: BatchView[] }>('/api/ui/v1/imports');
    batches.value = page.items;
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  }
}

async function selectBatch(batch: BatchView) {
  selected.value = batch;
  rejections.value = await apiGet<RejectionView[]>(`/api/ui/v1/imports/${batch.id}/rejections`);
  held.value = await apiGet<HeldView[]>(`/api/ui/v1/imports/${batch.id}/held`);
}

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files && input.files[0];
  if (file) {
    form.value.fileName = file.name;
    form.value.content = await file.text();
  }
}

async function submitUpload() {
  message.value = '';
  uploadError.value = '';
  try {
    const result = await apiPost<{ linesRead: number; linesLoaded: number; linesRejected: number; linesOnHold: number }>(
      '/api/ui/v1/imports',
      {
        entityCode: entityCode(),
        flowCode: form.value.flowCode,
        sourceSystem: form.value.sourceSystem,
        fileName: form.value.fileName,
        content: form.value.content,
      },
    );
    message.value = `Importé : ${result.linesLoaded} intégrée(s), ${result.linesRejected} rejetée(s), ${result.linesOnHold} en attente.`;
    await loadJournal();
  } catch (e) {
    uploadError.value = e instanceof ApiError ? e.message : "Échec de l'import";
  }
}

async function correct(rejection: RejectionView) {
  await apiPost(`/api/ui/v1/imports/rejections/${rejection.id}/correct`, {});
  await refreshSelected();
}

async function discard(rejection: RejectionView) {
  await apiPost(`/api/ui/v1/imports/rejections/${rejection.id}/discard`, {});
  await refreshSelected();
}

async function refreshSelected() {
  if (selected.value) {
    await selectBatch(selected.value);
    await loadJournal();
  }
}

function stateClass(status: string): string {
  if (status === 'LOADED') return 'tag ok';
  if (status === 'PARTIALLY_LOADED') return 'tag warn';
  if (status === 'REFUSED') return 'tag ko';
  return 'tag mute';
}

onMounted(loadJournal);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Imports et rejets</h1>
      <div class="page__subtitle">
        Chaque fichier est contrôlé ligne par ligne : une ligne fausse est rejetée, une ligne juste mais
        non traitable est intégrée en attente. Un fichier déjà intégré est refusé.
      </div>
    </div>

    <section v-if="canOperate()" class="card" style="padding: 18px 20px" data-testid="upload-form">
      <h2 style="margin: 0 0 12px; font-size: 16px">Déposer un fichier</h2>
      <div style="display: flex; flex-wrap: wrap; gap: 12px; align-items: end">
        <label>Flux
          <select data-testid="upload-flow" v-model="form.flowCode">
            <option v-for="f in FLOWS" :key="f" :value="f">{{ f }}</option>
          </select>
        </label>
        <label>Système source
          <input data-testid="upload-source" type="text" v-model="form.sourceSystem" />
        </label>
        <label>Fichier
          <input data-testid="upload-file" type="file" @change="onFile" />
        </label>
        <button class="btn" data-testid="upload-submit" @click="submitUpload">Importer</button>
      </div>
      <div style="margin-top: 12px; display: flex; gap: 10px">
        <span v-if="message" class="tag ok" data-testid="upload-message">{{ message }}</span>
        <span v-if="uploadError" class="tag ko" data-testid="upload-error">{{ uploadError }}</span>
      </div>
    </section>

    <div v-if="loadError" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">{{ loadError }}</div>

    <section v-else class="card" style="overflow-x: auto">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Journal des imports</h2>
      <table>
        <thead>
          <tr><th>Flux</th><th>Fichier</th><th>Reçu le</th><th class="num">Lues</th><th class="num">Filtrées</th>
            <th class="num">Intégrées</th><th class="num">Rejetées</th><th class="num">En attente</th><th>État</th></tr>
        </thead>
        <tbody>
          <tr v-if="batches.length === 0"><td colspan="9" data-testid="empty">Aucun import.</td></tr>
          <tr v-for="b in batches" :key="b.id" :data-testid="`batch-row-${b.id}`" style="cursor: pointer" @click="selectBatch(b)">
            <td>{{ b.flowCode }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace; font-size: 13px">{{ b.fileName }}</td>
            <td>{{ formatDate(b.receivedAt) }}</td>
            <td class="num">{{ b.linesRead }}</td>
            <td class="num">{{ b.linesFiltered }}</td>
            <td class="num">{{ b.linesLoaded }}</td>
            <td class="num">{{ b.linesRejected }}</td>
            <td class="num">{{ b.linesOnHold }}</td>
            <td><span :class="stateClass(b.status)" data-testid="batch-state">{{ b.status }}</span></td>
          </tr>
        </tbody>
      </table>
      <div style="padding: 12px 18px; font-size: 13px; color: #4A5868">
        Lues = filtrées + intégrées + rejetées + en attente.
      </div>
    </section>

    <section v-if="selected" class="card" style="overflow-x: auto" data-testid="batch-detail">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Lignes à traiter · {{ selected.fileName }}</h2>
      <table>
        <thead><tr><th class="num">Ligne</th><th>Motif</th><th>Code</th><th>Statut</th><th>Action</th></tr></thead>
        <tbody>
          <tr v-if="rejections.length === 0 && held.length === 0"><td colspan="5" data-testid="detail-empty">Aucune ligne en anomalie.</td></tr>
          <tr v-for="r in rejections" :key="`r-${r.id}`" :data-testid="`rejection-row-${r.id}`">
            <td class="num">{{ r.lineNumber }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace; font-size: 12px">{{ r.rawLine }}</td>
            <td><span class="tag ko">{{ r.reasonCode }}</span></td>
            <td>{{ r.status }}</td>
            <td>
              <template v-if="canOperate() && r.status === 'OPEN'">
                <button class="btn sec" :data-testid="`correct-${r.id}`" @click="correct(r)">Corriger et relancer</button>
                <button class="btn sec" :data-testid="`discard-${r.id}`" @click="discard(r)">Écarter</button>
              </template>
            </td>
          </tr>
          <tr v-for="(h, i) in held" :key="`h-${i}`" :data-testid="`held-row-${i}`">
            <td class="num">—</td>
            <td style="font-family: 'IBM Plex Mono', monospace; font-size: 12px">{{ h.sourceKey }} · {{ h.contractNumber ?? '—' }}</td>
            <td><span class="tag info">{{ h.holdReason }}</span></td>
            <td>ON_HOLD</td>
            <td><span style="font-size: 13px; color: #4A5868">Repart seule à l'arrivée de la donnée manquante</span></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card" style="overflow-x: auto">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Flux attendus sur la période</h2>
      <table>
        <thead><tr><th>Flux</th><th>Situation</th></tr></thead>
        <tbody>
          <tr v-for="f in EXPECTED" :key="f.code" :data-testid="`flow-${f.code}`">
            <td>{{ f.label }}</td>
            <td>
              <span v-if="receivedFlows.has(f.code)" class="tag ok">Reçu</span>
              <span v-else class="tag warn">Manquant</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
