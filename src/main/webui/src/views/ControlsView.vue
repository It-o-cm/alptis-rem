<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle, severityTag, severityLabel, decisionLabel } from '../presentation/cycle';
import HBarChart from '../components/charts/HBarChart.vue';
import { AppButton, AppCard, AppMessage, DataTable, EntitySelect, FormField, MonthSelect, PageHeading } from '../components/ui';
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
  versionNo: number;
  parameters: string | null;
}

const SEVERITIES = [
  { value: 'INFO', label: 'Information' },
  { value: 'WARNING', label: 'Avertissement' },
  { value: 'BLOCKING', label: 'Bloquant' },
];

interface ChecklistItem {
  code: string;
  label: string;
  checked: boolean;
  checkedBy: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 ? session.entities[0] : '');
const month = ref('2026-09-01');
const anomalies = ref<AnomalyView[]>([]);
const catalog = ref<CatalogEntry[]>([]);
const checklist = ref<ChecklistItem[]>([]);
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

/** Columns of the control checklist table. */
const checklistColumns: Column<ChecklistItem>[] = [
  { key: 'label', label: 'Contrôle' },
  { key: 'checked', label: 'Revu', value: (c) => (c.checked ? 'Oui' : 'À faire') },
  { key: 'checkedBy', label: 'Par', value: (c) => c.checkedBy ?? '—' },
];

/** Columns of the control catalog table. */
const catalogColumns: Column<CatalogEntry>[] = [
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'family', label: 'Famille' },
  { key: 'severity', label: 'Gravité' },
  { key: 'active', label: 'Actif', value: (c) => (c.active ? 'Oui' : 'Non') },
  { key: 'versionNo', label: 'Version', align: 'num', value: (c) => String(c.versionNo) },
];

/** Columns of the control-version history table. */
const versionColumns: Column<CatalogEntry>[] = [
  { key: 'versionNo', label: 'Version', align: 'num', value: (c) => String(c.versionNo) },
  { key: 'severity', label: 'Gravité' },
  { key: 'active', label: 'Actif', value: (c) => (c.active ? 'Oui' : 'Non') },
  { key: 'parameters', label: 'Paramètres', value: (c) => c.parameters ?? '—' },
];

/** Whether the current user may edit the control catalog (CTL-01). */
const canEditCatalog = (): boolean => session.roles.includes('BUSINESS_ADMIN');

const editing = ref<{ code: string; severity: string; active: boolean; parameters: string } | null>(null);
const versions = ref<{ code: string; rows: CatalogEntry[] } | null>(null);

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
/** The number of anomalies still open (undecided), shown as a running counter. */
const openAnomalyCount = computed(() => anomalies.value.filter((a) => a.decision === 'OPEN').length);

/** Loads the control catalog of the selected entity. */
async function loadCatalog(): Promise<void> {
  catalog.value = await apiGet<CatalogEntry[]>(`/api/ui/v1/controls/${entity.value}/catalog`);
}

/**
 * Opens the edit form for a catalog control, pre-filled with its current values.
 * @param c the control
 */
function startEdit(c: CatalogEntry): void {
  editing.value = { code: c.code, severity: c.severity, active: c.active, parameters: c.parameters ?? '' };
}

/** Saves the edited control as a new version, then reloads the catalog. */
async function saveEdit(): Promise<void> {
  if (!editing.value) return;
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/controls/${entity.value}/catalog/${editing.value.code}`, {
      severity: editing.value.severity,
      active: editing.value.active,
      parameters: editing.value.parameters === '' ? null : editing.value.parameters,
    });
    message.value = `Contrôle « ${editing.value.code} » versionné.`;
    editing.value = null;
    await loadCatalog();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Édition refusée';
  }
}

/**
 * Loads the version history of a control.
 * @param c the control
 */
async function loadVersions(c: CatalogEntry): Promise<void> {
  const rows = await apiGet<CatalogEntry[]>(`/api/ui/v1/controls/${entity.value}/catalog/${c.code}/versions`);
  versions.value = { code: c.code, rows };
}

/** Loads the manual control checklist of the selected period (CTL-03). */
async function loadChecklist(): Promise<void> {
  checklist.value = await apiGet<ChecklistItem[]>(
    `/api/ui/v1/controls/${entity.value}/${month.value}/checklist`,
  );
}

/**
 * Ticks or un-ticks a control in the checklist, then reloads it.
 * @param item the checklist item
 */
async function toggleCheck(item: ChecklistItem): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/controls/${entity.value}/${month.value}/checklist/${item.code}`, {
      checked: !item.checked,
    });
    await loadChecklist();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Action refusée';
  }
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
  await loadChecklist();
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
          <EntitySelect :id="id" data-testid="ctl-entity" v-model="entity" />
        </FormField>
        <FormField label="Mois" stacked width="short" v-slot="{ id }">
          <MonthSelect :id="id" data-testid="ctl-month" v-model="month" :entity="entity" />
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
      <p class="page__subtitle">
        Anomalies ouvertes : <span data-testid="ctl-open-count">{{ openAnomalyCount }}</span>
      </p>
      <DataTable
        :columns="anomalyColumns"
        :rows="anomalies"
        row-key="id"
        empty="Aucune anomalie."
        empty-testid="no-anomaly"
        :row-testid="(a) => `anomaly-row-${a.id}`"
      >
        <template #cell-severity="{ row }"><span :class="tag(row.severity)">{{ severityLabel(row.severity) }}</span></template>
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
            <AppButton variant="secondary" :data-testid="`anomaly-fix-${row.id}`" @click="decide(row, 'FIXED')">
              Corriger
            </AppButton>
            <AppButton variant="secondary" :data-testid="`anomaly-defer-${row.id}`" @click="decide(row, 'DEFERRED')">
              Reporter
            </AppButton>
          </template>
        </template>
      </DataTable>
    </AppCard>

    <AppCard title="Liste de contrôle de la période" data-testid="checklist">
      <DataTable
        :columns="checklistColumns"
        :rows="checklist"
        row-key="code"
        empty="Aucun contrôle actif."
        empty-testid="no-checklist"
        :row-testid="(c) => `checklist-row-${c.code}`"
      >
        <template #cell-checked="{ row }">
          <span :class="row.checked ? 'tag ok' : 'tag mute'" :data-testid="`checklist-state-${row.code}`">
            {{ row.checked ? 'Oui' : 'À faire' }}
          </span>
        </template>
        <template #actions="{ row }">
          <AppButton
            v-if="canOperate()"
            variant="secondary"
            :data-testid="`checklist-toggle-${row.code}`"
            @click="toggleCheck(row)"
          >
            {{ row.checked ? 'Annuler' : 'Marquer revu' }}
          </AppButton>
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
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`catalog-versions-${row.code}`" @click="loadVersions(row)">
            Versions
          </AppButton>
          <AppButton
            v-if="canEditCatalog()"
            variant="secondary"
            :data-testid="`catalog-edit-${row.code}`"
            @click="startEdit(row)"
          >
            Éditer
          </AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="editing" data-testid="catalog-edit-form" :title="`Éditer le contrôle ${editing.code}`">
      <div class="ui-bar">
        <FormField label="Gravité" stacked width="short" v-slot="{ id }">
          <select :id="id" data-testid="edit-severity" v-model="editing.severity">
            <option v-for="s in SEVERITIES" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
        </FormField>
        <FormField label="Paramètres (JSON)" stacked width="short" v-slot="{ id }">
          <input :id="id" data-testid="edit-parameters" type="text" v-model="editing.parameters" />
        </FormField>
        <FormField label="Actif" stacked width="short" v-slot="{ id }">
          <input :id="id" type="checkbox" data-testid="edit-active" v-model="editing.active" />
        </FormField>
        <AppButton data-testid="edit-save" @click="saveEdit">Enregistrer la version</AppButton>
      </div>
    </AppCard>

    <AppCard v-if="versions" data-testid="catalog-versions" :title="`Versions du contrôle ${versions.code}`">
      <DataTable
        :columns="versionColumns"
        :rows="versions.rows"
        row-key="versionNo"
        empty="Aucune version."
        empty-testid="no-version"
        :row-testid="(c) => `version-row-${c.versionNo}`"
      />
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
