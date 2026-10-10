<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle, severityTag, decisionLabel } from '../presentation/cycle';
import HBarChart from '../components/charts/HBarChart.vue';
import { AppButton, AppCard, AppMessage, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

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

/** Whether the current user may operate the cycle (run controls, decide). */
const canOperate = (): boolean => canOperateCycle(session.roles);

const breakdown = ref<Breakdown | null>(null);

/** Columns of the anomalies table. */
const anomalyColumns: Column<AnomalyView>[] = [
  { key: 'controlCode', label: 'Contrôle' },
  { key: 'severity', label: 'Gravité' },
  { key: 'object', label: 'Objet', value: (a) => `${a.objectType} #${a.objectId}` },
  { key: 'observedValue', label: 'Constaté' },
  { key: 'expectedValue', label: 'Attendu' },
  { key: 'decision', label: 'Décision' },
];

/** Columns of the control catalog table. */
const catalogColumns: Column<CatalogEntry>[] = [
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'family', label: 'Famille' },
  { key: 'severity', label: 'Gravité' },
  { key: 'active', label: 'Actif', value: (c) => (c.active ? 'Oui' : 'Non') },
];

/** Loads the anomalies and their breakdown for the selected period. */
async function loadAnomalies(): Promise<void> {
  error.value = '';
  try {
    anomalies.value = await apiGet<AnomalyView[]>(`/api/ui/v1/controls/${entity.value}/${month.value}/anomalies`);
    breakdown.value = await apiGet<Breakdown>(`/api/ui/v1/controls/${entity.value}/${month.value}/anomaly-breakdown`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

/** Anomaly counts by severity, shaped for the bar chart. */
const severityData = computed(() =>
  breakdown.value ? breakdown.value.bySeverity.map((c) => ({ label: c.label, value: c.count })) : [],
);
/** Anomaly counts by control family, shaped for the bar chart. */
const familyData = computed(() =>
  breakdown.value ? breakdown.value.byFamily.map((c) => ({ label: c.label, value: c.count })) : [],
);

/** Loads the control catalog of the selected entity. */
async function loadCatalog(): Promise<void> {
  catalog.value = await apiGet<CatalogEntry[]>(`/api/ui/v1/controls/${entity.value}/catalog`);
}

/** Runs the controls of the selected period, then reloads the anomalies. */
async function run(): Promise<void> {
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

/**
 * Records a decision on an anomaly, then reloads.
 * @param a the anomaly
 * @param decision the decision code
 */
async function decide(a: AnomalyView, decision: string): Promise<void> {
  await apiPost(`/api/ui/v1/controls/anomalies/${a.id}/decision`,
    { decision, justification: justification.value[a.id] ?? '' });
  await loadAnomalies();
}

/**
 * Tag class for an anomaly severity.
 * @param severity the severity code
 */
function tag(severity: string): string {
  return severityTag(severity);
}

/**
 * French label of a decision code.
 * @param decision the decision code
 */
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
    <PageHeading
      title="Contrôles et anomalies"
      subtitle="Les contrôles de la période, leurs anomalies et la décision prise sur chacune (section 8.6)."
    />

    <AppCard>
      <div class="ui-bar">
        <FormField label="Entité" stacked width="short" v-slot="{ id }">
          <input :id="id" data-testid="ctl-entity" type="text" v-model="entity" />
        </FormField>
        <FormField label="Mois" stacked width="short" v-slot="{ id }">
          <input :id="id" data-testid="ctl-month" type="text" v-model="month" />
        </FormField>
        <AppButton variant="secondary" data-testid="ctl-refresh" @click="loadAnomalies">Rafraîchir</AppButton>
        <AppButton v-if="canOperate()" data-testid="ctl-run" @click="run">Lancer les contrôles</AppButton>
      </div>
    </AppCard>

    <AppMessage v-if="error" variant="error" data-testid="ctl-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="ctl-message">{{ message }}</AppMessage>

    <div v-if="breakdown && breakdown.bySeverity.length > 0" class="ui-chart-grid">
      <AppCard flush>
        <HBarChart :data="severityData" title="Anomalies par sévérité" format="count" testid="chart-ctl-severity" />
      </AppCard>
      <AppCard flush>
        <HBarChart :data="familyData" title="Anomalies par famille de contrôle" format="count" testid="chart-ctl-family" />
      </AppCard>
    </div>

    <AppCard flush data-testid="anomalies">
      <DataTable
        :columns="anomalyColumns"
        :rows="anomalies"
        row-key="id"
        empty="Aucune anomalie."
        empty-testid="no-anomaly"
        :row-testid="(a) => `anomaly-row-${a.id}`"
      >
        <template #cell-severity="{ row }"><span :class="tag(row.severity)">{{ row.severity }}</span></template>
        <template #cell-decision="{ row }">
          <span :data-testid="`anomaly-decision-${row.id}`">{{ decided(row.decision) }}</span>
        </template>
        <template #actions="{ row }">
          <template v-if="canOperate()">
            <input
              :data-testid="`anomaly-justif-${row.id}`"
              type="text"
              v-model="justification[row.id]"
              placeholder="Justification"
            />
            <AppButton variant="secondary" :data-testid="`anomaly-accept-${row.id}`" @click="decide(row, 'ACCEPTED')">
              Accepter
            </AppButton>
          </template>
        </template>
      </DataTable>
    </AppCard>

    <AppCard title="Catalogue des contrôles" data-testid="catalog">
      <DataTable
        :columns="catalogColumns"
        :rows="catalog"
        row-key="code"
        empty="Aucun contrôle."
        empty-testid="no-catalog"
        :row-testid="(c) => `catalog-row-${c.code}`"
      >
        <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.ui-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-4);
}
.ui-chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--space-4);
}
</style>
