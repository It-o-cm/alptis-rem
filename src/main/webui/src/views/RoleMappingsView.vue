<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, apiDelete, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canManageRoleMappings } from '../presentation/access';
import { ROLES } from '../presentation/roles';

interface RoleMappingView {
  directoryGroup: string;
  role: string;
  entityCode: string | null;
}

const session = useSessionStore();
const mappings = ref<RoleMappingView[]>([]);
const loading = ref(true);
const loadError = ref('');
const form = ref({ directoryGroup: '', role: 'VIEWER', entityCode: '' });
const message = ref('');
const formError = ref('');

const canManage = () => canManageRoleMappings(session.roles);

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    mappings.value = await apiGet<RoleMappingView[]>('/api/ui/v1/role-mappings');
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

async function create() {
  message.value = '';
  formError.value = '';
  try {
    await apiPost('/api/ui/v1/role-mappings', {
      directoryGroup: form.value.directoryGroup,
      role: form.value.role,
      entityCode: form.value.entityCode === '' ? null : form.value.entityCode,
    });
    message.value = 'Correspondance ajoutée.';
    form.value = { directoryGroup: '', role: 'VIEWER', entityCode: '' };
    await reload();
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : "Échec de l'ajout";
  }
}

async function remove(mapping: RoleMappingView) {
  message.value = '';
  formError.value = '';
  const params = new URLSearchParams({ group: mapping.directoryGroup, role: mapping.role });
  if (mapping.entityCode) {
    params.set('entity', mapping.entityCode);
  }
  try {
    await apiDelete(`/api/ui/v1/role-mappings?${params.toString()}`);
    message.value = 'Correspondance supprimée.';
    await reload();
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : 'Échec de la suppression';
  }
}

onMounted(reload);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Groupes et rôles</h1>
      <div class="page__subtitle">Correspondance entre groupes d'annuaire et rôles ; un changement prend effet à la connexion suivante (ADM-01).</div>
    </div>

    <div v-if="loadError" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">{{ loadError }}</div>

    <section v-else class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Groupe d'annuaire</th><th>Rôle</th><th>Entité</th><th v-if="canManage()"></th></tr></thead>
        <tbody>
          <tr v-if="!loading && mappings.length === 0"><td colspan="4" data-testid="empty">Aucune correspondance.</td></tr>
          <tr v-for="m in mappings" :key="`${m.directoryGroup}:${m.role}:${m.entityCode}`" :data-testid="`mapping-row-${m.directoryGroup}-${m.role}`">
            <td style="font-family: 'IBM Plex Mono', monospace">{{ m.directoryGroup }}</td>
            <td>{{ m.role }}</td>
            <td>{{ m.entityCode ?? 'Toutes' }}</td>
            <td v-if="canManage()">
              <button class="btn sec" :data-testid="`delete-${m.directoryGroup}-${m.role}`" @click="remove(m)">Supprimer</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="canManage()" class="card" style="padding: 18px 20px" data-testid="mapping-form">
      <h2 style="margin: 0 0 12px; font-size: 16px">Ajouter une correspondance</h2>
      <div style="display: flex; flex-wrap: wrap; gap: 12px; align-items: end">
        <label>Groupe
          <input data-testid="field-group" type="text" v-model="form.directoryGroup" />
        </label>
        <label>Rôle
          <select data-testid="field-role" v-model="form.role">
            <option v-for="r in ROLES" :key="r" :value="r">{{ r }}</option>
          </select>
        </label>
        <label>Entité (vide = toutes)
          <input data-testid="field-entity" type="text" v-model="form.entityCode" />
        </label>
        <button class="btn" data-testid="create-mapping" @click="create">Ajouter</button>
      </div>
      <div style="margin-top: 12px; display: flex; gap: 10px">
        <span v-if="message" class="tag ok" data-testid="mapping-message">{{ message }}</span>
        <span v-if="formError" class="tag ko" data-testid="mapping-error">{{ formError }}</span>
      </div>
    </section>
  </div>
</template>
