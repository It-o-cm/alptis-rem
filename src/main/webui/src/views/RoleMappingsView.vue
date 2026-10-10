<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, apiDelete, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canManageRoleMappings } from '../presentation/access';
import { ROLES } from '../presentation/roles';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

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

/** Whether the current user may manage role mappings. */
const canManage = () => canManageRoleMappings(session.roles);

/** Columns of the role-mappings table. */
const columns: Column<RoleMappingView>[] = [
  { key: 'directoryGroup', label: "Groupe d'annuaire" },
  { key: 'role', label: 'Rôle' },
  { key: 'entityCode', label: 'Entité', value: (m) => m.entityCode ?? 'Toutes' },
];

/** Loads the directory-group to role mappings. */
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

/** Creates a new mapping from the form. */
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

/**
 * Deletes a mapping.
 * @param mapping the mapping to remove
 */
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
    <PageHeading
      title="Groupes et rôles"
      subtitle="Correspondance entre groupes d'annuaire et rôles ; un changement prend effet à la connexion suivante (ADM-01)."
    />

    <AppMessage v-if="loadError" variant="error" data-testid="load-error">{{ loadError }}</AppMessage>

    <AppCard v-else flush>
      <DataTable
        :columns="columns"
        :rows="mappings"
        :loading="loading"
        :row-key="(m) => `${m.directoryGroup}:${m.role}:${m.entityCode}`"
        empty="Aucune correspondance."
        empty-testid="empty"
        :row-testid="(m) => `mapping-row-${m.directoryGroup}-${m.role}`"
      >
        <template #cell-directoryGroup="{ row }"><span class="mono">{{ row.directoryGroup }}</span></template>
        <template #actions="{ row }">
          <AppButton
            v-if="canManage()"
            variant="danger"
            :data-testid="`delete-${row.directoryGroup}-${row.role}`"
            @click="remove(row)"
          >
            Supprimer
          </AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="canManage()" title="Ajouter une correspondance" data-testid="mapping-form">
      <AppForm @submit="create">
        <FormField label="Groupe" width="short" v-slot="{ id }">
          <input :id="id" data-testid="field-group" type="text" v-model="form.directoryGroup" />
        </FormField>
        <FormField label="Rôle" width="short" v-slot="{ id }">
          <select :id="id" data-testid="field-role" v-model="form.role">
            <option v-for="r in ROLES" :key="r" :value="r">{{ r }}</option>
          </select>
        </FormField>
        <FormField label="Entité (vide = toutes)" width="short" v-slot="{ id }">
          <input :id="id" data-testid="field-entity" type="text" v-model="form.entityCode" />
        </FormField>
        <AppButton type="submit" data-testid="create-mapping">Ajouter</AppButton>
      </AppForm>
      <AppMessage v-if="message" variant="success" data-testid="mapping-message">{{ message }}</AppMessage>
      <AppMessage v-if="formError" variant="error" data-testid="mapping-error">{{ formError }}</AppMessage>
    </AppCard>
  </div>
</template>
