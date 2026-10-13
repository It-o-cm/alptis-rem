<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { healthLabel } from '../presentation/imports';
import { AppCard, AppMessage, DetailList, DetailRow, PageHeading } from '../components/ui';

interface Health {
  status: string;
  pendingEvents: number;
  heldEvents: number;
  openPeriods: number;
  lastImportAt: string | null;
  lastExportAt: string | null;
  lastCalcAt: string | null;
}

const data = ref<Health | null>(null);
const error = ref('');

/**
 * Formats a timestamp for display, or a dash when absent.
 * @param value the timestamp, or null
 */
function at(value: string | null): string {
  return value ?? '—';
}

/** Loads the supervision health snapshot. */
async function load() {
  error.value = '';
  try {
    data.value = await apiGet<Health>('/api/ui/v1/admin/supervision');
  } catch (e) {
    data.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
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
