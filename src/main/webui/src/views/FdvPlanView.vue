<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { formatAmount } from '../presentation/format';
import { jobCodeLabel, JOB_CODES, componentLabel, PLAN_COMPONENTS } from '../presentation/salesforce';
import {
  AppButton, AppCard, AppForm, AppMessage, DataTable, EntitySelect, FilterBar, FormField, MonthSelect, PageHeading,
} from '../components/ui';
import type { Column } from '../components/ui';

interface Plan {
  jobCode: string;
  component: string;
  envelope: number;
  instalment: number | null;
  parameters: string | null;
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

/** Whether the current user may launch the variable calculation and manage the plans. */
const canRun = (): boolean => canEditEntityParameters(session.roles);

const jobCodes = JOB_CODES;
const planComponents = PLAN_COMPONENTS;
const planForm = ref({
  jobCode: 'CAC', component: 'MONTHLY_INDIVIDUAL', envelopeAmount: 2450, instalmentAmount: 146,
  parameters: '', validFrom: '2026-01-01', validTo: '',
});

/**
 * Formats a monetary value in euros.
 * @param value the amount to format
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Columns of the per-job envelope and instalment table. */
const planColumns: Column<Plan>[] = [
  { key: 'jobCode', label: 'Poste', value: (p) => jobCodeLabel(p.jobCode) },
  { key: 'component', label: 'Composante', value: (p) => componentLabel(p.component) },
  { key: 'envelope', label: 'Enveloppe', align: 'num', value: (p) => amount(p.envelope) },
  { key: 'instalment', label: 'Mensualité', align: 'num', value: (p) => (p.instalment === null ? '—' : amount(p.instalment)) },
  { key: 'parameters', label: 'Barème / critères', value: (p) => (p.parameters ?? '—') },
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

/** Saves (creates or updates) a variable plan from the plan form (FDV-04). */
async function savePlan(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/fdv/${entity.value}/plans`, {
      jobCode: planForm.value.jobCode,
      component: planForm.value.component,
      envelopeAmount: planForm.value.envelopeAmount,
      instalmentAmount: planForm.value.instalmentAmount,
      parameters: planForm.value.parameters || null,
      validFrom: planForm.value.validFrom || null,
      validTo: planForm.value.validTo || null,
    });
    message.value = 'Plan enregistré.';
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Enregistrement refusé';
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

/** Launches the quarterly qualitative variable for the quarter ending at the selected month (FDV-07). */
async function runQuarterlyQualitative(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ computed: number }>(
      `/api/ui/v1/fdv/${entity.value}/${month.value}/quarterly-qualitative`, {});
    message.value = `Variable trimestriel qualitatif lancé : ${result.computed} collaborateur(s).`;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Calcul refusé';
  }
}

const regYear = ref(2026);

/** Launches the annual individual regularization of January N+1 (FDV-06). */
async function runAnnualRegularization(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ regularized: number }>(
      `/api/ui/v1/fdv/${entity.value}/${regYear.value}/annual-regularization`, {});
    message.value = `Régularisation annuelle ${regYear.value} lancée : ${result.regularized} collaborateur(s).`;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Régularisation refusée';
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
        <EntitySelect :id="id" data-testid="fdvp-entity" v-model="entity" />
      </FormField>
      <FormField label="Mois" stacked width="date" v-slot="{ id }">
        <MonthSelect :id="id" data-testid="fdvp-month" v-model="month" :entity="entity" value-format="date" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="fdvp-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="fdvp-message">{{ message }}</AppMessage>

    <div v-if="canRun()" class="fdvp-actions">
      <AppButton variant="secondary" data-testid="fdvp-run" @click="run">Lancer le calcul</AppButton>
      <AppButton variant="secondary" data-testid="fdvp-quarterly" @click="runQuarterlyQualitative">
        Variable trimestriel
      </AppButton>
      <FormField label="Année à régulariser" stacked width="short" v-slot="{ id }">
        <input :id="id" data-testid="fdvp-reg-year" type="number" v-model.number="regYear" />
      </FormField>
      <AppButton variant="secondary" data-testid="fdvp-reg" @click="runAnnualRegularization">
        Régularisation annuelle
      </AppButton>
    </div>

    <AppCard title="Plans de variable par poste">
      <DataTable
        :columns="planColumns"
        :rows="plans"
        :loading="loading"
        empty="Aucun plan."
        empty-testid="no-plan"
        :row-testid="(p) => `plan-row-${plans.indexOf(p)}`"
      />

      <AppForm v-if="canRun()" two-column @submit="savePlan">
        <FormField label="Poste" width="short" v-slot="{ id }">
          <select :id="id" data-testid="plan-job" v-model="planForm.jobCode">
            <option v-for="code in jobCodes" :key="code" :value="code">{{ jobCodeLabel(code) }}</option>
          </select>
        </FormField>
        <FormField label="Composante" width="short" v-slot="{ id }">
          <select :id="id" data-testid="plan-component" v-model="planForm.component">
            <option v-for="c in planComponents" :key="c" :value="c">{{ componentLabel(c) }}</option>
          </select>
        </FormField>
        <FormField label="Enveloppe annuelle" width="short" v-slot="{ id }">
          <input :id="id" data-testid="plan-envelope" type="number" step="any" v-model.number="planForm.envelopeAmount" />
        </FormField>
        <FormField label="Mensualité" width="short" v-slot="{ id }">
          <input :id="id" data-testid="plan-instalment" type="number" step="any" v-model.number="planForm.instalmentAmount" />
        </FormField>
        <FormField label="Barème / critères / règles d'absence (JSON)" width="long" v-slot="{ id }">
          <input :id="id" data-testid="plan-parameters" v-model="planForm.parameters"
                 placeholder='{"growthTiers":{"15":1000,"25":2000,"35":3000}}' />
        </FormField>
        <FormField label="Valable du" width="date" v-slot="{ id }">
          <input :id="id" data-testid="plan-from" type="date" v-model="planForm.validFrom" />
        </FormField>
        <FormField label="Au" width="date" v-slot="{ id }">
          <input :id="id" data-testid="plan-to" type="date" v-model="planForm.validTo" />
        </FormField>
        <AppButton type="submit" data-testid="plan-save">Enregistrer le plan</AppButton>
      </AppForm>
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

<style scoped>
.fdvp-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
}
</style>
