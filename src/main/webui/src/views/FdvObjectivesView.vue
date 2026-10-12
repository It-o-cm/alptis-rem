<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { jobCodeLabel, JOB_CODES, objectiveTypeLabel, OBJECTIVE_TYPES } from '../presentation/salesforce';
import { formatAmount } from '../presentation/format';
import {
  AppButton, AppCard, AppForm, AppMessage, DataTable, FilterBar, FormField, PageHeading,
} from '../components/ui';
import type { Column } from '../components/ui';

interface Objective {
  employee: string | null;
  type: string;
  periodFrom: string | null;
  periodTo: string | null;
  targetValue: number;
  versionNo: number | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const objectives = ref<Objective[]>([]);
const error = ref('');
const message = ref('');
const loading = ref(false);

const canEdit = (): boolean => canEditEntityParameters(session.roles);
const jobCodes = JOB_CODES;
const objectiveTypes = OBJECTIVE_TYPES;

const form = ref({
  employeeExternalId: '', jobCode: 'CAC', objectiveType: 'INDIVIDUAL_QUANTITATIVE',
  periodFrom: '2026-01-01', periodTo: '2026-12-31', targetValue: 1000,
});

/** Columns of the objectives table. */
const columns: Column<Objective>[] = [
  { key: 'scope', label: 'Portée', value: (o) => (o.employee ?? 'Collectif') },
  { key: 'type', label: 'Type', value: (o) => objectiveTypeLabel(o.type) },
  { key: 'period', label: 'Période', value: (o) => `${o.periodFrom ?? '—'} → ${o.periodTo ?? '—'}` },
  { key: 'targetValue', label: 'Cible', align: 'num', value: (o) => formatAmount(o.targetValue) },
  { key: 'versionNo', label: 'Version', align: 'num', value: (o) => (o.versionNo ?? '—') },
];

/** Loads the objectives of the selected entity. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    objectives.value = await apiGet<Objective[]>(`/api/ui/v1/fdv/${entity.value}/objectives`);
  } catch (e) {
    objectives.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/** Submits the objective form, creating a new (versioned) objective. */
async function submit(): Promise<void> {
  try {
    await apiPost(`/api/ui/v1/fdv/${entity.value}/objectives`, {
      employeeExternalId: form.value.employeeExternalId || null,
      jobCode: form.value.jobCode,
      objectiveType: form.value.objectiveType,
      periodFrom: form.value.periodFrom || null,
      periodTo: form.value.periodTo || null,
      targetValue: form.value.targetValue,
    });
    message.value = 'Objectif enregistré.';
    error.value = '';
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
    message.value = '';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Objectifs"
      subtitle="Objectifs individuels et collectifs, quantitatifs et qualitatifs, par poste et par période, versionnés (FDV-03). Un nouvel objectif de même clé crée une version ; le calcul lit la dernière."
    />

    <FilterBar :loading="loading" search-label="Rafraîchir" search-testid="fdvo-refresh" :resettable="false" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="fdvo-entity" type="text" v-model="entity" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="fdvo-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="fdvo-message">{{ message }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="objectives"
        :loading="loading"
        :row-key="(o) => `${o.employee ?? 'C'}-${o.type}-${o.periodFrom}-${o.versionNo}`"
        empty="Aucun objectif."
        empty-testid="no-objective"
        :row-testid="(o) => `objective-row-${o.employee ?? 'collectif'}-${o.type}-v${o.versionNo}`"
      />
    </AppCard>

    <AppCard v-if="canEdit()" title="Nouvel objectif" data-testid="objective-form">
      <AppForm two-column @submit="submit">
        <FormField label="Type" width="short" v-slot="{ id }">
          <select :id="id" data-testid="obj-type" v-model="form.objectiveType">
            <option v-for="t in objectiveTypes" :key="t" :value="t">{{ objectiveTypeLabel(t) }}</option>
          </select>
        </FormField>
        <FormField label="Poste" width="short" v-slot="{ id }">
          <select :id="id" data-testid="obj-job" v-model="form.jobCode">
            <option v-for="code in jobCodes" :key="code" :value="code">{{ jobCodeLabel(code) }}</option>
          </select>
        </FormField>
        <FormField label="Collaborateur (vide = collectif)" width="short" v-slot="{ id }">
          <input :id="id" data-testid="obj-employee" v-model="form.employeeExternalId" />
        </FormField>
        <FormField label="Cible" width="short" v-slot="{ id }">
          <input :id="id" data-testid="obj-target" type="number" step="any" v-model.number="form.targetValue" />
        </FormField>
        <FormField label="Du" width="date" v-slot="{ id }">
          <input :id="id" data-testid="obj-from" type="date" v-model="form.periodFrom" />
        </FormField>
        <FormField label="Au" width="date" v-slot="{ id }">
          <input :id="id" data-testid="obj-to" type="date" v-model="form.periodTo" />
        </FormField>
        <AppButton type="submit" data-testid="obj-submit">Enregistrer l'objectif</AppButton>
      </AppForm>
    </AppCard>
  </div>
</template>
