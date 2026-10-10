<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle, severityTag, decisionLabel } from '../presentation/cycle';
import HBarChart from '../components/charts/HBarChart.vue';

interface Count {
  label: string;
  count: number;
}
interface Breakdown {
  bySeverity: Count[];
  byFamily: Count[];
}

interface AnomalyView {
  id: number;
  controlCode: string | null;
  severity: string;
  objectType: string;
  objectId: number;
  observedValue: string;
  expectedValue: string;
  decision: string;
  decidedBy: string | null;
  justification: string | null;
}

interface CatalogEntry {
  code: string;
  label: string;
  family: string;
  severity: string;
  active: boolean;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 ? session.entities[0] : '');
const month = ref('2026-09-01');
const anomalies = ref<AnomalyView[]>([]);
const catalog = ref<CatalogEntry[]>([]);
const error = ref('');
const message = ref('');
const justification = ref<Record<number, string>>({});

const canOperate = () => canOperateCycle(session.roles);

const breakdown = ref<Breakdown | null>(null);

async function loadAnomalies() {
  error.value = '';
  try {
    anomalies.value = await apiGet<AnomalyView[]>(`/api/ui/v1/controls/${entity.value}/${month.value}/anomalies`);
    breakdown.value = await apiGet<Breakdown>(`/api/ui/v1/controls/${entity.value}/${month.value}/anomaly-breakdown`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

const severityData = computed(() =>
  breakdown.value ? breakdown.value.bySeverity.map((c) => ({ label: c.label, value: c.count })) : [],
);
const familyData = computed(() =>
  breakdown.value ? breakdown.value.byFamily.map((c) => ({ label: c.label, value: c.count })) : [],
);

async function loadCatalog() {
  catalog.value = await apiGet<CatalogEntry[]>(`/api/ui/v1/controls/${entity.value}/catalog`);
}

async function run() {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ anomalies: number }>(
      `/api/ui/v1/controls/${entity.value}/${month.value}/run`, {});
    message.value = `${result.anomalies} anomalie(s) détectée(s).`;
    await loadAnomalies();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Contrôles refusés';
  }
}

async function decide(a: AnomalyView, decision: string) {
  await apiPost(`/api/ui/v1/controls/anomalies/${a.id}/decision`,
    { decision, justification: justification.value[a.id] ?? '' });
  await loadAnomalies();
}

function tag(severity: string): string {
  return severityTag(severity);
}

function decided(decision: string): string {
  return decisionLabel(decision);
}

onMounted(async () => {
  await loadAnomalies();
  await loadCatalog();
});
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Contrôles et anomalies</h1>
      <div class="page__subtitle">
        Les contrôles de la période, leurs anomalies et la décision prise sur chacune (section 8.6).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="ctl-entity" type="text" v-model="entity" /></label>
        <label>Mois <input data-testid="ctl-month" type="text" v-model="month" /></label>
        <button class="btn" data-testid="ctl-refresh" @click="loadAnomalies">Rafraîchir</button>
        <button v-if="canOperate()" class="btn" data-testid="ctl-run" @click="run">Lancer les contrôles</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="ctl-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="ctl-message" style="margin-top: 10px">{{ message }}</div>
    </section>

    <div v-if="breakdown && breakdown.bySeverity.length > 0"
         style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 14px">
      <section class="card">
        <HBarChart :data="severityData" title="Anomalies par sévérité" format="count" testid="chart-ctl-severity" />
      </section>
      <section class="card">
        <HBarChart :data="familyData" title="Anomalies par famille de contrôle" format="count" testid="chart-ctl-family" />
      </section>
    </div>

    <section class="card" style="overflow-x: auto" data-testid="anomalies">
      <table>
        <thead>
          <tr><th>Contrôle</th><th>Gravité</th><th>Objet</th><th>Constaté</th><th>Attendu</th><th>Décision</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="anomalies.length === 0"><td colspan="7" data-testid="no-anomaly">Aucune anomalie.</td></tr>
          <tr v-for="a in anomalies" :key="a.id" :data-testid="`anomaly-row-${a.id}`">
            <td>{{ a.controlCode }}</td>
            <td><span :class="tag(a.severity)">{{ a.severity }}</span></td>
            <td>{{ a.objectType }} #{{ a.objectId }}</td>
            <td>{{ a.observedValue }}</td>
            <td>{{ a.expectedValue }}</td>
            <td :data-testid="`anomaly-decision-${a.id}`">{{ decided(a.decision) }}</td>
            <td v-if="canOperate()">
              <input :data-testid="`anomaly-justif-${a.id}`" type="text" v-model="justification[a.id]"
                     placeholder="Justification" />
              <button class="btn" :data-testid="`anomaly-accept-${a.id}`" @click="decide(a, 'ACCEPTED')">Accepter</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card" style="overflow-x: auto" data-testid="catalog">
      <h2 style="margin: 12px 20px; font-size: 16px">Catalogue des contrôles</h2>
      <table>
        <thead>
          <tr><th>Code</th><th>Libellé</th><th>Famille</th><th>Gravité</th><th>Actif</th></tr>
        </thead>
        <tbody>
          <tr v-if="catalog.length === 0"><td colspan="5" data-testid="no-catalog">Aucun contrôle.</td></tr>
          <tr v-for="c in catalog" :key="c.code" :data-testid="`catalog-row-${c.code}`">
            <td>{{ c.code }}</td>
            <td>{{ c.label }}</td>
            <td>{{ c.family }}</td>
            <td>{{ c.severity }}</td>
            <td>{{ c.active ? 'Oui' : 'Non' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
