<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { periodStateLabel } from '../presentation/cycle';
import { runTypeLabel, runStatusLabel, runStatusTag, RECALC_SCOPES } from '../presentation/calc';
import { holdReasonLabel } from '../presentation/events';
import { formatDate } from '../presentation/format';
import {
  AppButton,
  AppCard,
  AppMessage,
  DataTable,
  FilterBar,
  FormField,
  PageHeading,
} from '../components/ui';
import type { Column } from '../components/ui';

interface PeriodSummary {
  entityCode: string;
  entityName: string;
  month: string;
  state: string;
}

interface RunView {
  id: number;
  type: string;
  status: string;
  launchedBy: string;
  startedAt: string;
  finishedAt: string | null;
  processed: number;
  lines: number;
  partialScope: string | null;
}

interface FailedUnit {
  sourceKey: string;
  contractNumber: string | null;
  coverageCode: string | null;
  holdReason: string | null;
}

interface ExecutionsView {
  runs: RunView[];
  failed: FailedUnit[];
}

const session = useSessionStore();
const periods = ref<PeriodSummary[]>([]);
const selected = ref<PeriodSummary | null>(null);
const executions = ref<ExecutionsView | null>(null);
const error = ref('');
const message = ref('');
const busy = ref(false);
const recalc = ref({ type: 'DISTRIBUTOR', value: '' });

const canLaunch = (): boolean =>
  session.roles.includes('OPERATOR') || session.roles.includes('BUSINESS_ADMIN');

/** Columns of the runs table. */
const runColumns: Column<RunView>[] = [
  { key: 'type', label: 'Type', value: (r) => runTypeLabel(r.type) },
  { key: 'partialScope', label: 'Périmètre', value: (r) => r.partialScope ?? 'Complet' },
  { key: 'launchedBy', label: 'Lancé par' },
  { key: 'startedAt', label: 'Début', value: (r) => formatDate(r.startedAt) },
  { key: 'processed', label: 'Unités', align: 'num', value: (r) => String(r.processed) },
  { key: 'lines', label: 'Lignes', align: 'num', value: (r) => String(r.lines) },
  { key: 'status', label: 'Statut' },
];

/** Columns of the failed-units table. */
const failedColumns: Column<FailedUnit>[] = [
  { key: 'sourceKey', label: 'Clé source' },
  { key: 'contractNumber', label: 'Adhésion', value: (f) => f.contractNumber ?? '—' },
  { key: 'coverageCode', label: 'Garantie', value: (f) => f.coverageCode ?? '—' },
  { key: 'holdReason', label: 'Motif', value: (f) => holdReasonLabel(f.holdReason) },
];

/** Loads the open periods of the user's entities. */
async function loadPeriods(): Promise<void> {
  error.value = '';
  try {
    periods.value = await apiGet<PeriodSummary[]>('/api/ui/v1/periods');
    if (periods.value.length > 0) {
      await select(periods.value[0]);
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

/** Selects a period and loads its executions. */
async function select(p: PeriodSummary): Promise<void> {
  selected.value = p;
  executions.value = await apiGet<ExecutionsView>(`/api/ui/v1/calculations/${p.entityCode}/${p.month}`);
}

/** Launches a full real calculation of the selected period. */
async function launch(): Promise<void> {
  if (!selected.value) return;
  await act(() =>
    apiPost<ExecutionsView>(`/api/ui/v1/calculations/${selected.value!.entityCode}/${selected.value!.month}/run`, {}),
    'Calcul lancé.',
  );
}

/** Launches a partial recalculation scoped to the chosen distributor, product or rule. */
async function launchPartialRecalc(): Promise<void> {
  if (!selected.value || !recalc.value.value) return;
  const p = selected.value;
  const params = new URLSearchParams({ type: recalc.value.type, value: recalc.value.value });
  await act(
    () => apiPost<ExecutionsView>(`/api/ui/v1/calculations/${p.entityCode}/${p.month}/recalc?${params}`, {}),
    'Recalcul partiel lancé.',
  );
}

/** Runs an action that returns the refreshed executions, mapping errors to a French message. */
async function act(fn: () => Promise<ExecutionsView>, ok: string): Promise<void> {
  message.value = '';
  error.value = '';
  busy.value = true;
  try {
    executions.value = await fn();
    message.value = ok;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Action refusée';
  } finally {
    busy.value = false;
  }
}

onMounted(loadPeriods);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Exécutions de calcul"
      subtitle="Lancer un calcul, suivre les exécutions et leurs compteurs, voir les unités en échec et relancer un périmètre (CAL-01, CAL-12)."
    />

    <AppMessage v-if="error" variant="error" data-testid="calc-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="calc-message">{{ message }}</AppMessage>

    <FilterBar>
      <FormField label="Période" width="short" v-slot="{ id }">
        <select
          :id="id"
          data-testid="calc-period"
          :value="selected ? `${selected.entityCode}|${selected.month}` : ''"
          @change="(e) => {
            const [c, m] = (e.target as HTMLSelectElement).value.split('|');
            const p = periods.find((x) => x.entityCode === c && x.month === m);
            if (p) select(p);
          }"
        >
          <option v-for="p in periods" :key="`${p.entityCode}|${p.month}`" :value="`${p.entityCode}|${p.month}`">
            {{ p.entityName }} — {{ p.month }} ({{ periodStateLabel(p.state) }})
          </option>
        </select>
      </FormField>
      <AppButton v-if="canLaunch()" :loading="busy" data-testid="calc-run" @click="launch">
        Lancer le calcul
      </AppButton>
    </FilterBar>

    <AppCard v-if="canLaunch() && selected" data-testid="calc-recalc" title="Recalcul partiel">
      <FilterBar>
        <FormField label="Périmètre" width="short" v-slot="{ id }">
          <select :id="id" data-testid="recalc-type" v-model="recalc.type">
            <option v-for="s in RECALC_SCOPES" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
        </FormField>
        <FormField label="Code" width="short" v-slot="{ id }">
          <input :id="id" data-testid="recalc-value" type="text" v-model="recalc.value" />
        </FormField>
        <AppButton variant="secondary" :loading="busy" data-testid="recalc-run" @click="launchPartialRecalc">
          Relancer le périmètre
        </AppButton>
      </FilterBar>
    </AppCard>

    <AppCard v-if="executions" flush data-testid="calc-runs">
      <DataTable
        :columns="runColumns"
        :rows="executions.runs"
        empty="Aucune exécution."
        empty-testid="no-run"
        :row-testid="(r) => `run-row-${r.id}`"
      >
        <template #cell-status="{ row }">
          <span :class="runStatusTag(row.status)">{{ runStatusLabel(row.status) }}</span>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="executions" data-testid="calc-failed" title="Unités en échec">
      <DataTable
        :columns="failedColumns"
        :rows="executions.failed"
        empty="Aucune unité en échec."
        empty-testid="no-failed"
        :row-testid="(f) => `failed-row-${f.sourceKey}`"
      />
    </AppCard>
  </div>
</template>
