<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, apiPut, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { jobCodeLabel, JOB_CODES } from '../presentation/salesforce';
import {
  AppButton, AppCard, AppForm, AppMessage, DataTable, EntitySelect, FilterBar, FormField, PageHeading,
} from '../components/ui';
import type { Column } from '../components/ui';

interface Collaborator {
  externalId: string;
  lastName: string | null;
  firstName: string | null;
  jobCode: string | null;
  team: string | null;
  region: string | null;
  manager: string | null;
  validFrom: string | null;
  validTo: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const collaborators = ref<Collaborator[]>([]);
const error = ref('');
const message = ref('');
const loading = ref(false);
const editing = ref(false);

const canEdit = (): boolean => canEditEntityParameters(session.roles);

/** A blank collaborator form. */
function blank(): Collaborator {
  return {
    externalId: '', lastName: '', firstName: '', jobCode: 'CAC', team: '', region: '', manager: '',
    validFrom: '2026-01-01', validTo: null,
  };
}

const form = ref<Collaborator>(blank());

/** Columns of the collaborators table. */
const columns: Column<Collaborator>[] = [
  { key: 'externalId', label: 'Matricule' },
  { key: 'lastName', label: 'Nom', value: (c) => c.lastName ?? '—' },
  { key: 'firstName', label: 'Prénom', value: (c) => c.firstName ?? '—' },
  { key: 'jobCode', label: 'Poste', value: (c) => jobCodeLabel(c.jobCode) },
  { key: 'team', label: 'Équipe', value: (c) => c.team ?? '—' },
  { key: 'region', label: 'Région', value: (c) => c.region ?? '—' },
  { key: 'manager', label: 'Responsable', value: (c) => c.manager ?? '—' },
  { key: 'validity', label: 'Validité', value: (c) => `${c.validFrom ?? '—'} → ${c.validTo ?? '—'}` },
];

/** The job codes offered in the post select. */
const jobCodes = JOB_CODES;

/** Records a failure, clearing any success. */
function fail(e: unknown): void {
  error.value = e instanceof ApiError ? e.message : 'Erreur';
  message.value = '';
}

/** Loads the collaborators of the selected entity. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    collaborators.value = await apiGet<Collaborator[]>(`/api/ui/v1/fdv/${entity.value}/collaborators`);
  } catch (e) {
    collaborators.value = [];
    fail(e);
  } finally {
    loading.value = false;
  }
}

/** Resets the form fields to a blank new collaborator, leaving any message untouched. */
function resetForm(): void {
  form.value = blank();
  editing.value = false;
}

/** Resets the form and clears the messages (the explicit "new"/"cancel" action). */
function newCollaborator(): void {
  resetForm();
  message.value = '';
  error.value = '';
}

/**
 * Loads a collaborator into the form for editing.
 * @param c the collaborator row
 */
function edit(c: Collaborator): void {
  form.value = {
    externalId: c.externalId, lastName: c.lastName ?? '', firstName: c.firstName ?? '',
    jobCode: c.jobCode ?? 'CAC', team: c.team ?? '', region: c.region ?? '', manager: c.manager ?? '',
    validFrom: c.validFrom, validTo: c.validTo,
  };
  editing.value = true;
  message.value = '';
  error.value = '';
}

/** Submits the form, creating or updating the collaborator. */
async function submit(): Promise<void> {
  const body = {
    externalId: form.value.externalId,
    lastName: form.value.lastName,
    firstName: form.value.firstName,
    jobCode: form.value.jobCode,
    team: form.value.team || null,
    region: form.value.region || null,
    managerExternalId: form.value.manager || null,
    validFrom: form.value.validFrom || null,
    validTo: form.value.validTo || null,
  };
  try {
    if (editing.value) {
      await apiPut(`/api/ui/v1/fdv/${entity.value}/collaborators/${form.value.externalId}`, body);
      resetForm();
      message.value = 'Collaborateur mis à jour.';
    } else {
      await apiPost(`/api/ui/v1/fdv/${entity.value}/collaborators`, body);
      resetForm();
      message.value = 'Collaborateur créé.';
    }
    error.value = '';
    await load();
  } catch (e) {
    fail(e);
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Collaborateurs"
      subtitle="Gestion des collaborateurs de la force de vente : poste, équipe, région, responsable et dates (FDV-01). L'alimentation en masse se fait aussi par le SI RH (flux d'import)."
    />

    <FilterBar :loading="loading" search-label="Rafraîchir" search-testid="fdvcol-refresh" :resettable="false" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="fdvcol-entity" v-model="entity" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="fdvcol-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="fdvcol-message">{{ message }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="collaborators"
        :loading="loading"
        row-key="externalId"
        empty="Aucun collaborateur."
        empty-testid="no-collaborator"
        :row-testid="(c) => `collaborator-row-${c.externalId}`"
      >
        <template #cell-externalId="{ row }"><span class="mono">{{ row.externalId }}</span></template>
        <template #actions="{ row }">
          <AppButton
            v-if="canEdit()"
            variant="secondary"
            :data-testid="`collaborator-edit-${row.externalId}`"
            @click="edit(row)"
          >
            Éditer
          </AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="canEdit()" :title="editing ? `Modifier ${form.externalId}` : 'Nouveau collaborateur'"
             data-testid="collaborator-form">
      <AppForm two-column @submit="submit">
        <FormField label="Matricule" width="short" v-slot="{ id }">
          <input :id="id" data-testid="collab-external-id" v-model="form.externalId" :disabled="editing" />
        </FormField>
        <FormField label="Poste" width="short" v-slot="{ id }">
          <select :id="id" data-testid="collab-job" v-model="form.jobCode">
            <option v-for="code in jobCodes" :key="code" :value="code">{{ jobCodeLabel(code) }}</option>
          </select>
        </FormField>
        <FormField label="Nom" width="short" v-slot="{ id }">
          <input :id="id" data-testid="collab-last-name" v-model="form.lastName" />
        </FormField>
        <FormField label="Prénom" width="short" v-slot="{ id }">
          <input :id="id" data-testid="collab-first-name" v-model="form.firstName" />
        </FormField>
        <FormField label="Équipe" width="short" v-slot="{ id }">
          <input :id="id" data-testid="collab-team" v-model="form.team" />
        </FormField>
        <FormField label="Région" width="short" v-slot="{ id }">
          <input :id="id" data-testid="collab-region" v-model="form.region" />
        </FormField>
        <FormField label="Responsable (matricule)" width="short" v-slot="{ id }">
          <input :id="id" data-testid="collab-manager" v-model="form.manager" />
        </FormField>
        <FormField label="Valable du" width="date" v-slot="{ id }">
          <input :id="id" data-testid="collab-valid-from" type="date" v-model="form.validFrom" />
        </FormField>
        <FormField label="Au" width="date" v-slot="{ id }">
          <input :id="id" data-testid="collab-valid-to" type="date" v-model="form.validTo" />
        </FormField>
        <div class="collab-actions">
          <AppButton type="submit" data-testid="collab-submit">
            {{ editing ? 'Enregistrer' : 'Créer' }}
          </AppButton>
          <AppButton v-if="editing" variant="secondary" type="button" data-testid="collab-cancel" @click="newCollaborator">
            Annuler
          </AppButton>
        </div>
      </AppForm>
    </AppCard>
  </div>
</template>

<style scoped>
.collab-actions {
  display: flex;
  gap: var(--space-3);
  grid-column: 1 / -1;
}
</style>
