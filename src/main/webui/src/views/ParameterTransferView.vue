<script setup lang="ts">
import { ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const planCode = ref('');
const newCode = ref('');
const exported = ref<unknown | null>(null);
const json = ref('');
const error = ref('');
const message = ref('');

const canImport = () => canEditEntityParameters(session.roles);

async function exportPlan() {
  error.value = '';
  message.value = '';
  try {
    exported.value = await apiGet(`/api/ui/v1/parameters/${entity.value}/plans/${planCode.value}/export`);
    json.value = JSON.stringify(exported.value, null, 2);
  } catch (e) {
    exported.value = null;
    json.value = '';
    error.value = e instanceof ApiError ? e.message : 'Export impossible';
  }
}

async function importPlan() {
  error.value = '';
  message.value = '';
  try {
    const body = exported.value ?? JSON.parse(json.value);
    const result = await apiPost<{ planCode: string }>(
      `/api/ui/v1/parameters/${entity.value}/import?newCode=${newCode.value}`,
      body,
    );
    message.value = `Plan importé : ${result.planCode}`;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Import impossible';
  }
}
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Export et import du paramétrage</h1>
      <div class="page__subtitle">
        Exporte un plan complet au format ouvert et le réimporte sous un nouveau code, pour la promotion
        entre environnements et la réversibilité (PAR-15). Les versions importées sont en projet.
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="pt-entity" type="text" v-model="entity" /></label>
        <label>Plan à exporter <input data-testid="pt-plan" type="text" v-model="planCode" /></label>
        <button class="btn" data-testid="pt-export" @click="exportPlan">Exporter</button>
      </div>
      <div v-if="canImport()" style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap; margin-top: 12px">
        <label>Nouveau code <input data-testid="pt-newcode" type="text" v-model="newCode" /></label>
        <button class="btn" data-testid="pt-import" @click="importPlan">Importer sous ce code</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="pt-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="pt-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <section v-if="json" class="card" style="padding: 18px 20px" data-testid="pt-json-card">
      <h2 style="margin: 0 0 12px; font-size: 16px">Paramétrage exporté</h2>
      <textarea data-testid="pt-json" aria-label="Paramétrage exporté" v-model="json" rows="16"
                style="width: 100%; box-sizing: border-box; font-family: monospace; font-size: 12px"></textarea>
    </section>
  </div>
</template>
