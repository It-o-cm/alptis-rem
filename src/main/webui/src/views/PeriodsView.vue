<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { periodStateLabel, nextState, canDrivePeriod } from '../presentation/cycle';
import { AppButton, AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface PeriodSummary {
  entityCode: string;
  entityName: string;
  month: string;
  state: string;
}

interface TransitionView {
  fromState: string;
  toState: string;
  userId: string;
  reason: string | null;
}

interface PeriodDetail {
  period: PeriodSummary;
  transitions: TransitionView[];
  lines: number;
  openAnomalies: number;
  blocking: number;
}

const session = useSessionStore();
const periods = ref<PeriodSummary[]>([]);
const detail = ref<PeriodDetail | null>(null);
const error = ref('');
const message = ref('');
const loading = ref(false);
const advancing = ref(false);

const canDrive = (): boolean => canDrivePeriod(session.roles);

/** Columns of the periods list. */
const columns: Column<PeriodSummary>[] = [
  { key: 'entityName', label: 'Entité' },
  { key: 'month', label: 'Mois' },
  { key: 'state', label: 'État' },
];

/** Columns of the transitions table. */
const transitionColumns: Column<TransitionView>[] = [
  { key: 'fromState', label: 'De', value: (t) => periodStateLabel(t.fromState) },
  { key: 'toState', label: 'Vers', value: (t) => periodStateLabel(t.toState) },
  { key: 'userId', label: 'Par' },
  { key: 'reason', label: 'Motif', value: (t) => t.reason ?? '—' },
];

/** Loads the open periods of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    periods.value = await apiGet<PeriodSummary[]>('/api/ui/v1/periods');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/**
 * Loads the detail of a period.
 * @param p the selected period
 */
async function open(p: PeriodSummary): Promise<void> {
  detail.value = await apiGet<PeriodDetail>(`/api/ui/v1/periods/${p.entityCode}/${p.month}`);
}

/** The next cycle state the current period can move to, or null. */
function target(): string | null {
  return detail.value ? nextState(detail.value.period.state) : null;
}

/** Advances the open period one step in the monthly cycle. */
async function advance(): Promise<void> {
  if (!detail.value) return;
  const to = target();
  if (!to) return;
  message.value = '';
  error.value = '';
  advancing.value = true;
  try {
    const p = detail.value.period;
    await apiPost(`/api/ui/v1/periods/${p.entityCode}/${p.month}/transition`, { toState: to });
    message.value = `Période avancée vers « ${periodStateLabel(to)} ».`;
    await load();
    await open({ ...p });
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Transition refusée';
  } finally {
    advancing.value = false;
  }
}

/**
 * French label of a cycle state.
 * @param state the state code
 */
function label(state: string): string {
  return periodStateLabel(state);
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Avancement des périodes"
      subtitle="Les périodes ouvertes de vos entités et leur état dans le cycle mensuel (section 7)."
    />

    <AppMessage v-if="error" variant="error" data-testid="periods-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="periods-message">{{ message }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="periods"
        :loading="loading"
        empty="Aucune période ouverte."
        empty-testid="no-period"
        :row-testid="(p) => `period-row-${p.entityCode}-${p.month}`"
      >
        <template #cell-state="{ row }"><span class="tag mute">{{ label(row.state) }}</span></template>
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`open-${row.entityCode}-${row.month}`" @click="open(row)">
            Ouvrir
          </AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="period-detail">
      <h2 class="ui-detail-title">
        {{ detail.period.entityName }} — {{ detail.period.month }}
        <span class="tag mute" data-testid="detail-state">{{ label(detail.period.state) }}</span>
      </h2>
      <p class="page__subtitle" data-testid="detail-counts">
        {{ detail.lines }} ligne(s), {{ detail.openAnomalies }} anomalie(s) ouverte(s),
        {{ detail.blocking }} bloquante(s).
      </p>
      <div v-if="canDrive() && target()">
        <AppButton :loading="advancing" data-testid="advance-button" @click="advance">
          Avancer vers « {{ label(target() as string) }} »
        </AppButton>
      </div>
      <DataTable
        :columns="transitionColumns"
        :rows="detail.transitions"
        empty="Aucune transition."
        empty-testid="no-transition"
        :row-testid="() => 'transition-row'"
      />
    </AppCard>
  </div>
</template>

<style scoped>
.ui-detail-title {
  margin: 0;
  font-size: var(--fs-h2);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
</style>
