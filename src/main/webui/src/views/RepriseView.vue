<script setup lang="ts">
import { ref } from 'vue';
import { apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateImports } from '../presentation/access';

interface UploadResponse {
  id: number;
  status: string;
  linesRead: number;
  linesFiltered: number;
  linesLoaded: number;
  linesRejected: number;
  linesOnHold: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const sourceSystem = ref('REPRISE');
const content = ref('payee_type;payee_code;opening_balance\n');
const result = ref<UploadResponse | null>(null);
const error = ref('');

const canUpload = () => canOperateImports(session.roles);

async function upload() {
  error.value = '';
  try {
    result.value = await apiPost<UploadResponse>('/api/ui/v1/imports', {
      entityCode: entity.value,
      flowCode: 'opening_balances',
      sourceSystem: sourceSystem.value,
      fileName: 'opening_balances.csv',
      content: content.value,
    });
  } catch (e) {
    result.value = null;
    error.value = e instanceof ApiError ? e.message : 'Dépôt impossible';
  }
}
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Reprise d'historique</h1>
      <div class="page__subtitle">
        Dépôt des fichiers d'ouverture à la bascule d'un périmètre : soldes des comptes repris en écriture
        d'ouverture, contrôlés ligne à ligne (ING-11). Colonnes : payee_type ; payee_code ; opening_balance.
      </div>
    </div>

    <section v-if="canUpload()" class="card" style="padding: 18px 20px" data-testid="reprise-form">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="reprise-entity" type="text" v-model="entity" /></label>
        <label>Système source <input data-testid="reprise-source" type="text" v-model="sourceSystem" /></label>
      </div>
      <label for="reprise-content" style="margin-top: 12px; display: block">Fichier d'ouverture (CSV)</label>
      <textarea id="reprise-content" aria-label="Fichier d'ouverture (CSV)" data-testid="reprise-content"
                v-model="content" rows="8"
                style="width: 100%; box-sizing: border-box; font-family: monospace; font-size: 12px"></textarea>
      <button class="btn" data-testid="reprise-upload" style="margin-top: 10px" @click="upload">Déposer</button>
      <div v-if="error" class="tag ko" data-testid="reprise-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <section v-if="result" class="card" style="overflow-x: auto" data-testid="reprise-result">
      <table>
        <thead><tr><th>Lues</th><th>Filtrées</th><th>Chargées</th><th>Rejetées</th><th>En attente</th><th>Statut</th></tr></thead>
        <tbody>
          <tr>
            <td>{{ result.linesRead }}</td>
            <td>{{ result.linesFiltered }}</td>
            <td data-testid="reprise-loaded">{{ result.linesLoaded }}</td>
            <td data-testid="reprise-rejected">{{ result.linesRejected }}</td>
            <td data-testid="reprise-onhold">{{ result.linesOnHold }}</td>
            <td>{{ result.status }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
