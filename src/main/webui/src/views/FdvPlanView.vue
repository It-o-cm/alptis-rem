<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { formatAmount } from '../presentation/format';

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

const canRun = () => canEditEntityParameters(session.roles);

function amount(value: number): string {
  return formatAmount(value);
}

async function load() {
  error.value = '';
  try {
    plans.value = await apiGet<Plan[]>(`/api/ui/v1/fdv/${entity.value}/plans`);
    collaborators.value = await apiGet<Collaborator[]>(`/api/ui/v1/fdv/${entity.value}/collaborators`);
    objectives.value = await apiGet<Objective[]>(`/api/ui/v1/fdv/${entity.value}/objectives`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function run() {
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
    <div>
      <h1 class="page__title" data-testid="page-title">Plan de variable</h1>
      <div class="page__subtitle">
        Enveloppes et mensualités par poste, collaborateurs et objectifs versionnés ; l'administrateur
        métier lance le calcul avec la période (FDV-01, FDV-03, FDV-04, FDV-05).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="fdvp-entity" type="text" v-model="entity" /></label>
        <label>Mois <input data-testid="fdvp-month" type="text" v-model="month" /></label>
        <button class="btn" data-testid="fdvp-refresh" @click="load">Rafraîchir</button>
        <button v-if="canRun()" class="btn" data-testid="fdvp-run" @click="run">Lancer le calcul</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="fdvp-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="fdvp-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <section class="card" style="overflow-x: auto">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Enveloppes par poste</h2>
      <table>
        <thead><tr><th>Poste</th><th>Composante</th><th>Enveloppe</th><th>Mensualité</th><th>Validité</th></tr></thead>
        <tbody>
          <tr v-if="plans.length === 0"><td colspan="5" data-testid="no-plan">Aucun plan.</td></tr>
          <tr v-for="(p, i) in plans" :key="i" :data-testid="`plan-row-${i}`">
            <td>{{ p.jobCode }}</td><td>{{ p.component }}</td><td>{{ amount(p.envelope) }}</td>
            <td>{{ amount(p.instalment) }}</td><td>{{ p.validFrom }} → {{ p.validTo ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card" style="overflow-x: auto">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Collaborateurs</h2>
      <table>
        <thead><tr><th>Matricule</th><th>Nom</th><th>Poste</th><th>Équipe</th><th>Région</th><th>Responsable</th></tr></thead>
        <tbody>
          <tr v-if="collaborators.length === 0"><td colspan="6" data-testid="no-collab">Aucun collaborateur.</td></tr>
          <tr v-for="c in collaborators" :key="c.externalId" :data-testid="`collab-row-${c.externalId}`">
            <td>{{ c.externalId }}</td><td>{{ c.lastName }} {{ c.firstName }}</td><td>{{ c.jobCode }}</td>
            <td>{{ c.team ?? '—' }}</td><td>{{ c.region ?? '—' }}</td><td>{{ c.manager ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card" style="overflow-x: auto">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Objectifs</h2>
      <table>
        <thead><tr><th>Collaborateur</th><th>Type</th><th>Période</th><th>Cible</th><th>Version</th></tr></thead>
        <tbody>
          <tr v-if="objectives.length === 0"><td colspan="5" data-testid="no-objective">Aucun objectif.</td></tr>
          <tr v-for="(o, i) in objectives" :key="i" :data-testid="`objective-row-${i}`">
            <td>{{ o.employee ?? 'Collectif' }}</td><td>{{ o.type }}</td>
            <td>{{ o.periodFrom }} → {{ o.periodTo }}</td><td>{{ o.target }}</td><td>v{{ o.versionNo }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
