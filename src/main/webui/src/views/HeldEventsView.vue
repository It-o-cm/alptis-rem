<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { eventTypeLabel, holdReasonLabel } from '../presentation/events';
import { formatDate } from '../presentation/format';
import { AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface ReasonCount {
  reason: string;
  count: number;
}

interface EventView {
  id: number;
  sourceKey: string;
  eventType: string;
  contractNumber: string | null;
  eventDate: string;
  holdReason: string | null;
}

const reasons = ref<ReasonCount[]>([]);
const held = ref<EventView[]>([]);
const error = ref('');
const loading = ref(false);

/** Columns of the held-by-reason summary. */
const reasonColumns: Column<ReasonCount>[] = [
  { key: 'reason', label: 'Motif', value: (r) => holdReasonLabel(r.reason === '—' ? null : r.reason) },
  { key: 'count', label: 'Événements', align: 'num', value: (r) => String(r.count) },
];

/** Columns of the held events list. */
const eventColumns: Column<EventView>[] = [
  { key: 'eventType', label: 'Type', value: (e) => eventTypeLabel(e.eventType) },
  { key: 'sourceKey', label: 'Clé source' },
  { key: 'contractNumber', label: 'Adhésion', value: (e) => e.contractNumber ?? '—' },
  { key: 'eventDate', label: 'Date', value: (e) => formatDate(e.eventDate) },
  { key: 'holdReason', label: 'Motif', value: (e) => holdReasonLabel(e.holdReason) },
];

/** Loads the held-by-reason counts and the held events list. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    reasons.value = await apiGet<ReasonCount[]>('/api/ui/v1/events/held-by-reason');
    const page = await apiGet<{ items: EventView[] }>('/api/ui/v1/events?status=ON_HOLD');
    held.value = page.items;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Événements en attente"
      subtitle="Les événements mis à l'écart, regroupés par motif ; ils ne bloquent pas la période et sont repris dès que la donnée ou la règle manquante arrive (P8, ING-06)."
    />

    <AppMessage v-if="error" variant="error" data-testid="held-error">{{ error }}</AppMessage>

    <AppCard title="Par motif">
      <DataTable
        :columns="reasonColumns"
        :rows="reasons"
        :loading="loading"
        empty="Aucun événement en attente."
        empty-testid="no-reason"
        :row-testid="(r) => `reason-row-${r.reason}`"
      />
    </AppCard>

    <AppCard flush title="Événements en attente">
      <DataTable
        :columns="eventColumns"
        :rows="held"
        :loading="loading"
        empty="Aucun événement en attente."
        empty-testid="no-held-event"
        :row-testid="(e) => `held-row-${e.id}`"
      />
    </AppCard>
  </div>
</template>
