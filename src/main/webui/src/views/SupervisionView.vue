<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { healthLabel } from '../presentation/imports';
import { AppButton, AppCard, AppMessage, DataTable, DetailList, DetailRow, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface Health {
  status: string;
  pendingEvents: number;
  heldEvents: number;
  openPeriods: number;
  lastImportAt: string | null;
  lastExportAt: string | null;
  lastCalcAt: string | null;
}

interface FailedTreatment {
  entityCode: string;
  month: string;
  runType: string;
  failedAt: string | null;
}

const session = useSessionStore();
const data = ref<Health | null>(null);
const failed = ref<FailedTreatment[]>([]);
const error = ref('');
const message = ref('');

/** Whether the current user (technical administrator) may relaunch a failed treatment. */
const canRelaunch = (): boolean => session.roles.includes('IT_ADMIN');

/**
 * Formats a timestamp for display, or a dash when absent.
 * @param value the timestamp, or null
 */
function at(value: string | null): string {
  return value ?? '—';
}

/** Columns of the failed-treatments table. */
const failedColumns: Column<FailedTreatment>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'month', label: 'Période' },
  { key: 'runType', label: 'Traitement' },
  { key: 'failedAt', label: 'Échec', value: (t) => at(t.failedAt) },
];

/** Loads the supervision health snapshot and the failed treatments. */
async function load() {
  error.value = '';
  try {
    data.value = await apiGet<Health>('/api/ui/v1/admin/supervision');
    failed.value = await apiGet<FailedTreatment[]>('/api/ui/v1/admin/supervision/failed');
  } catch (e) {
    data.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

/**
 * Relaunches a failed treatment by re-running its period's calculation.
 * @param treatment the failed treatment to relaunch
 */
async function relaunch(treatment: FailedTreatment): Promise<void> {
  error.value = '';
  message.value = '';
  try {
    failed.value = await apiPost<FailedTreatment[]>('/api/ui/v1/admin/supervision/relaunch', {
      entityCode: treatment.entityCode,
      month: treatment.month,
    });
    message.value = `Traitement relancé pour ${treatment.entityCode} ${treatment.month}.`;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Relance impossible';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Supervision des traitements et des flux"
      subtitle="État de santé et métriques des traitements : événements en attente, périodes ouvertes et derniers échanges (OUT-07, NFR-16)."
    />

    <AppMessage v-if="error" variant="error" data-testid="sup-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="sup-message">{{ message }}</AppMessage>

    <AppCard title="Traitements en échec" data-testid="sup-failed">
      <DataTable
        :columns="failedColumns"
        :rows="failed"
        empty="Aucun traitement en échec."
        empty-testid="sup-no-failed"
        :row-testid="(t) => `failed-row-${t.entityCode}-${t.month}`"
      >
        <template #actions="{ row }">
          <AppButton
            v-if="canRelaunch()"
            variant="secondary"
            :data-testid="`failed-relaunch-${row.entityCode}-${row.month}`"
            @click="relaunch(row)"
          >
            Relancer
          </AppButton>
        </template>
      </DataTable>
    </AppCard>

    <template v-if="data">
      <p><span class="tag ok" data-testid="sup-status">{{ healthLabel(data.status) }}</span></p>

      <div class="sup-tiles">
        <AppCard>
          <p class="sup-tile__label">Événements en attente</p>
          <p class="sup-tile__value" data-testid="sup-pending">{{ data.pendingEvents }}</p>
        </AppCard>
        <AppCard>
          <p class="sup-tile__label">Événements gelés</p>
          <p class="sup-tile__value">{{ data.heldEvents }}</p>
        </AppCard>
        <AppCard>
          <p class="sup-tile__label">Périodes ouvertes</p>
          <p class="sup-tile__value">{{ data.openPeriods }}</p>
        </AppCard>
      </div>

      <AppCard>
        <DetailList>
          <DetailRow label="Dernier import"><span data-testid="sup-import">{{ at(data.lastImportAt) }}</span></DetailRow>
          <DetailRow label="Dernier export">{{ at(data.lastExportAt) }}</DetailRow>
          <DetailRow label="Dernier calcul">{{ at(data.lastCalcAt) }}</DetailRow>
        </DetailList>
      </AppCard>
    </template>
  </div>
</template>

<style scoped>
.sup-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-4);
}
.sup-tile__label {
  margin: 0;
  font-size: var(--fs-small);
  color: var(--muted);
}
.sup-tile__value {
  margin: 0;
  font: 500 var(--fs-title) 'IBM Plex Mono', monospace;
}
</style>
