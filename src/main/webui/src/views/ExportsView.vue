<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canProduceExport, exportStatusLabel } from '../presentation/reporting';
import { formatAmount } from '../presentation/format';

interface ExportRow {
  id: number;
  flowCode: string;
  fileName: string;
  fileHash: string;
  author: string;
  lines: number;
  total: number;
  status: string;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const rows = ref<ExportRow[]>([]);
const content = ref('');
const error = ref('');
const message = ref('');
const form = ref({ object: 'COMMISSION_LINES', period: '2026-09-01', data: false });

const canProduce = () => canProduceExport(session.roles);

function statusLabel(status: string): string {
  return exportStatusLabel(status);
}

function amount(value: number): string {
  return formatAmount(value);
}

async function load() {
  error.value = '';
  content.value = '';
  try {
    rows.value = await apiGet<ExportRow[]>(`/api/ui/v1/flows/${entity.value}/exports`);
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function produce() {
  message.value = '';
  error.value = '';
  try {
    const q = form.value.data ? '' : `&period=${form.value.period}`;
    await apiPost(`/api/ui/v1/flows/${entity.value}/exports?object=${form.value.object}${q}`, {});
    message.value = 'Export produit.';
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Production refusée';
  }
}

async function download(row: ExportRow) {
  error.value = '';
  try {
    content.value = await apiGetText(`/api/ui/v1/flows/exports/${row.id}/content`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Téléchargement impossible';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Exports de la période</h1>
      <div class="page__subtitle">
        Chaque fichier émis est inscrit au journal des exports avec son empreinte, son nombre de lignes et
        son total : on sait toujours ce qui est parti, quand, vers qui et avec quel contenu (section 9.11).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="exports-entity" type="text" v-model="entity" /></label>
        <button class="btn" data-testid="exports-refresh" @click="load">Rafraîchir</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="exports-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="exports-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <section v-if="canProduce()" class="card" style="padding: 18px 20px" data-testid="exports-form">
      <h2 style="margin: 0 0 12px; font-size: 16px">Produire un export</h2>
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Objet
          <select data-testid="exports-object" v-model="form.object">
            <option value="COMMISSION_LINES">Lignes de commission</option>
            <option value="ACCOUNTS">Comptes et soldes</option>
          </select>
        </label>
        <label>Période <input data-testid="exports-period" type="text" v-model="form.period" :disabled="form.data" /></label>
        <label>Export Data (sans période) <input data-testid="exports-data" type="checkbox" v-model="form.data" /></label>
        <button class="btn" data-testid="exports-produce" @click="produce">Produire</button>
      </div>
    </section>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Flux</th><th>Fichier</th><th>Empreinte</th><th>Auteur</th><th>Lignes</th><th>Total</th><th>État</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="8" data-testid="no-export">Aucun export.</td></tr>
          <tr v-for="r in rows" :key="r.id" :data-testid="`export-row-${r.id}`">
            <td>{{ r.flowCode }}</td>
            <td>{{ r.fileName }}</td>
            <td style="font-family: monospace; font-size: 12px">{{ r.fileHash.slice(0, 12) }}…</td>
            <td>{{ r.author }}</td>
            <td>{{ r.lines }}</td>
            <td>{{ amount(r.total) }}</td>
            <td :data-testid="`export-status-${r.id}`">{{ statusLabel(r.status) }}</td>
            <td><button class="btn" :data-testid="`export-download-${r.id}`" @click="download(r)">Télécharger</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="content" class="card" style="padding: 18px 20px" data-testid="export-content-card">
      <h2 style="margin: 0 0 12px; font-size: 16px">Contenu</h2>
      <pre data-testid="export-content" style="overflow-x: auto; background: #f6f8fa; padding: 12px">{{ content }}</pre>
    </section>
  </div>
</template>
