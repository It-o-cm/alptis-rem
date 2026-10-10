<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { formatAmount } from '../presentation/format';
import { AppButton, AppCard, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface Plan {
  jobCode: string;
  component: string;
  envelope: number;
  instalment: number;
  validFrom: string;
  validTo: string | null;
}

interface Collaborator {
  externalId: string;
  lastName: string;
  firstName: string;
  jobCode: string;
  team: string | null;
  region: string | null;
  manager: string | null;
}

interface Objective {
  employee: string | null;
  type: string;
  periodFrom: string;
  periodTo: string;
  target: number;
  versionNo: number;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const month = ref('2026-09-01');
const plans = ref<Plan[]>([]);
const collaborators = ref<Collaborator[]>([]);
const objectives = ref<Objective[]>([]);
const error = ref('');
const message = ref('');
const loading = ref(false);

/** Whether the current user may launch the variable calculation. */
const canRun = (): boolean => canEditEntityParameters(session.roles);

/**
 * Formats a monetary value in euros.
 * @param value the amount to format
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Columns of the per-job envelope and instalment table. */
const planColumns: Column<Plan>[] = [
  { key: 'jobCode', label: 'Poste' },
  { key: 'component', label: 'Composante' },
  { key: 'envelope', label: 'Enveloppe', align: 'num', value: (p) => amount(p.envelope) },
  { key: 'instalment', label: 'Mensualité', align: 'num', value: (p) => amount(p.instalment) },
  { key: 'validFrom', label: 'Validité', value: (p) => `${p.validFrom} → ${p.validTo ?? '—'}` },
];

/** Columns of the collaborators table. */
const collaboratorColumns: Column<Collaborator>[] = [
  { key: 'externalId', label: 'Matricule' },
  { key: 'lastName', label: 'Nom', value: (c) => `${c.lastName} ${c.firstName}` },
  { key: 'jobCode', label: 'Poste' },
  { key: 'team', label: 'Équipe', value: (c) => c.team ?? '—' },
  { key: 'region', label: 'Région', value: (c) => c.region ?? '—' },
  { key: 'manager', label: 'Responsable', value: (c) => c.manager ?? '—' },
];

/** Columns of the versioned objectives table. */
const objectiveColumns: Column<Objective>[] = [
  { key: 'employee', label: 'Collaborateur', value: (o) => o.employee ?? 'Collectif' },
  { key: 'type', label: 'Type' },
  { key: 'periodFrom', label: 'Période', value: (o) => `${o.periodFrom} → ${o.periodTo}` },
  { key: 'target', label: 'Cible', align: 'num' },
  { key: 'versionNo', label: 'Version', value: (o) => `v${o.versionNo}` },
];

/** Loads the plans, collaborators and objectives of the selected entity. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    plans.value = await apiGet<Plan[]>(`/api/ui/v1/fdv/${entity.value}/plans`);
    collaborators.value = await apiGet<Collaborator[]>(`/api/ui/v1/fdv/${entity.value}/collaborators`);
    objectives.value = await apiGet<Objective[]>(`/api/ui/v1/fdv/${entity.value}/objectives`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/** Launches the monthly variable calculation for the selected entity and month. */
async function run(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/fdv/${entity.value}/${month.value}/run`, {});
    message.value = 'Calcul du variable lancé.';
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Calcul refusé';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Plan de variable"
      subtitle="Enveloppes et mensualités par poste, collaborateurs et objectifs versionnés ; l'administrateur métier lance le calcul avec la période (FDV-01, FDV-03, FDV-04, FDV-05)."
    />

    <FilterBar :loading="loading" :resettable="false" search-label="Rafraîchir" search-testid="fdvp-refresh" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="fdvp-entity" type="text" v-model="entity" />
      </FormField>
      <FormField label="Mois" stacked width="date" v-slot="{ id }">
        <input :id="id" data-testid="fdvp-month" type="text" v-model="month" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="fdvp-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="fdvp-message">{{ message }}</AppMessage>

    <div v-if="canRun()">
      <AppButton variant="secondary" data-testid="fdvp-run" @click="run">Lancer le calcul</AppButton>
    </div>

    <AppCard title="Enveloppes par poste">
      <DataTable
        :columns="planColumns"
        :rows="plans"
        :loading="loading"
        empty="Aucun plan."
        empty-testid="no-plan"
        :row-testid="(p) => `plan-row-${plans.indexOf(p)}`"
      >
        <template #cell-jobCode="{ row }"><span class="mono">{{ row.jobCode }}</span></template>
      </DataTable>
    </AppCard>

    <AppCard title="Collaborateurs">
      <DataTable
        :columns="collaboratorColumns"
        :rows="collaborators"
        :loading="loading"
        row-key="externalId"
        empty="Aucun collaborateur."
        empty-testid="no-collab"
        :row-testid="(c) => `collab-row-${c.externalId}`"
      >
        <template #cell-externalId="{ row }"><span class="mono">{{ row.externalId }}</span></template>
        <template #cell-jobCode="{ row }"><span class="mono">{{ row.jobCode }}</span></template>
      </DataTable>
    </AppCard>

    <AppCard title="Objectifs">
      <DataTable
        :columns="objectiveColumns"
        :rows="objectives"
        :loading="loading"
        empty="Aucun objectif."
        empty-testid="no-objective"
        :row-testid="(o) => `objective-row-${objectives.indexOf(o)}`"
      />
    </AppCard>
  </div>
</template>
