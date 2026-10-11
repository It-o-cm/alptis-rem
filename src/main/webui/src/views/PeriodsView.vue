<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { periodStateLabel, nextState, canDrivePeriod, batchTypeLabel, paymentStatusLabel } from '../presentation/cycle';
import { formatAmount } from '../presentation/format';
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

interface CalendarMilestone {
  businessDay: string;
  milestone: string;
  reached: boolean;
}

interface LotView {
  reference: string;
  batchType: string;
  status: string;
  orderCount: number;
  totalAmount: number;
}

interface PeriodDetail {
  period: PeriodSummary;
  transitions: TransitionView[];
  lines: number;
  openAnomalies: number;
  blocking: number;
  heldEvents: number;
  pendingEvents: number;
  calendar: CalendarMilestone[];
  lots: LotView[];
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

/** Columns of the target-calendar table (section 7.3). */
const calendarColumns: Column<CalendarMilestone>[] = [
  { key: 'businessDay', label: 'Jalon' },
  { key: 'milestone', label: 'Étape' },
  { key: 'reached', label: 'Atteint', value: (m) => (m.reached ? 'Oui' : 'En attente') },
];

/** Columns of the period lots table (section 7.1). */
const lotColumns: Column<LotView>[] = [
  { key: 'batchType', label: 'Type', value: (l) => batchTypeLabel(l.batchType) },
  { key: 'reference', label: 'Référence' },
  { key: 'status', label: 'Statut', value: (l) => paymentStatusLabel(l.status) },
  { key: 'orderCount', label: 'Ordres', align: 'num', value: (l) => String(l.orderCount) },
  { key: 'totalAmount', label: 'Total', align: 'num', value: (l) => formatAmount(l.totalAmount) },
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
      <p class="page__subtitle" data-testid="detail-remaining">
        Reste à traiter : {{ detail.pendingEvents }} événement(s) en attente de calcul,
        {{ detail.heldEvents }} événement(s) mis à l’écart.
      </p>
      <div v-if="canDrive() && target()">
        <AppButton :loading="advancing" data-testid="advance-button" @click="advance">
          Avancer vers « {{ label(target() as string) }} »
        </AppButton>
      </div>

      <h3 class="ui-detail-section">Calendrier cible (section 7.3)</h3>
      <DataTable
        :columns="calendarColumns"
        :rows="detail.calendar"
        empty="Aucun jalon."
        empty-testid="no-milestone"
        :row-testid="(m) => `milestone-row-${m.businessDay}`"
      >
        <template #cell-reached="{ row }">
          <span :class="row.reached ? 'tag ok' : 'tag mute'" :data-testid="`milestone-reached-${row.reached}`">
            {{ row.reached ? 'Oui' : 'En attente' }}
          </span>
        </template>
      </DataTable>

      <h3 class="ui-detail-section">Lots de paiement de la période</h3>
      <DataTable
        :columns="lotColumns"
        :rows="detail.lots"
        empty="Aucun lot de paiement."
        empty-testid="no-lot"
        :row-testid="(l) => `lot-row-${l.batchType}`"
      />

      <h3 class="ui-detail-section">Historique des transitions</h3>
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

.ui-detail-section {
  margin: var(--space-5) 0 var(--space-2);
  font-size: var(--fs-h3);
}
</style>
