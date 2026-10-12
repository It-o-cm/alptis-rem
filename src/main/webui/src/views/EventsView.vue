<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import {
  eventStatusLabel,
  eventTypeLabel,
  holdReasonLabel,
  STATUS_OPTIONS,
  EVENT_TYPE_OPTIONS,
} from '../presentation/events';
import { formatAmount, formatDate } from '../presentation/format';
import { lineNatureLabel, directionLabel, lineStatusLabel } from '../presentation/lines';
import { AppButton, AppCard, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface EventView {
  id: number;
  sourceKey: string;
  sourceSystem: string;
  eventType: string | null;
  contractNumber: string | null;
  eventDate: string;
  status: string;
  holdReason: string | null;
  lineCount: number;
  entityCode: string;
}

interface ProducedLine {
  nature: string | null;
  direction: string | null;
  amount: number | null;
  status: string | null;
}

interface EventDetail {
  event: EventView;
  lines: ProducedLine[];
}

const rows = ref<EventView[]>([]);
const detail = ref<EventDetail | null>(null);
const error = ref('');
const loading = ref(false);
const filters = ref({ q: '', status: '', type: '' });

/** Columns of the events search. */
const columns: Column<EventView>[] = [
  { key: 'eventType', label: 'Type', value: (e) => eventTypeLabel(e.eventType) },
  { key: 'sourceKey', label: 'Clé source' },
  { key: 'contractNumber', label: 'Adhésion', value: (e) => e.contractNumber ?? '—' },
  { key: 'eventDate', label: 'Date', value: (e) => formatDate(e.eventDate) },
  { key: 'status', label: 'Statut', value: (e) => eventStatusLabel(e.status) },
  { key: 'holdReason', label: 'Motif d’attente', value: (e) => holdReasonLabel(e.holdReason) },
  { key: 'lineCount', label: 'Lignes', align: 'num', value: (e) => String(e.lineCount) },
];

/** Columns of the produced lines of an event. */
const lineColumns: Column<ProducedLine>[] = [
  { key: 'nature', label: 'Nature', value: (l) => lineNatureLabel(l.nature) },
  { key: 'direction', label: 'Sens', value: (l) => directionLabel(l.direction) },
  { key: 'status', label: 'Statut', value: (l) => lineStatusLabel(l.status) },
  { key: 'amount', label: 'Montant', align: 'num', value: (l) => formatAmount(l.amount ?? undefined) },
];

/** Searches events with the current filters. */
async function search(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    const params = new URLSearchParams();
    if (filters.value.q) params.set('q', filters.value.q);
    if (filters.value.status) params.set('status', filters.value.status);
    if (filters.value.type) params.set('type', filters.value.type);
    const page = await apiGet<{ items: EventView[] }>(`/api/ui/v1/events?${params.toString()}`);
    rows.value = page.items;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Recherche impossible';
  } finally {
    loading.value = false;
  }
}

/** Opens one event's detail. */
async function open(e: EventView): Promise<void> {
  detail.value = await apiGet<EventDetail>(`/api/ui/v1/events/${e.id}`);
}

onMounted(search);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Recherche d'événements"
      subtitle="Rechercher un événement et lire son statut, son motif d'attente et les lignes qu'il a produites (ING-06, section 5.3)."
    />

    <AppMessage v-if="error" variant="error" data-testid="events-error">{{ error }}</AppMessage>

    <FilterBar>
      <FormField label="Clé ou adhésion" width="short" v-slot="{ id }">
        <input :id="id" data-testid="events-q" type="text" v-model="filters.q" @keyup.enter="search" />
      </FormField>
      <FormField label="Statut" width="short" v-slot="{ id }">
        <select :id="id" data-testid="events-status" v-model="filters.status">
          <option value="">Tous</option>
          <option v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>
      </FormField>
      <FormField label="Type" width="short" v-slot="{ id }">
        <select :id="id" data-testid="events-type" v-model="filters.type">
          <option value="">Tous</option>
          <option v-for="t in EVENT_TYPE_OPTIONS" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
      </FormField>
      <AppButton data-testid="events-search" @click="search">Rechercher</AppButton>
    </FilterBar>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucun événement."
        empty-testid="no-event"
        :row-testid="(e) => `event-row-${e.id}`"
      >
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`open-event-${row.id}`" @click="open(row)">
            Détail
          </AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="event-detail" :title="`Événement ${detail.event.sourceKey}`">
      <dl class="ui-event-detail">
        <div><dt>Type</dt><dd data-testid="detail-type">{{ eventTypeLabel(detail.event.eventType) }}</dd></div>
        <div><dt>Statut</dt><dd data-testid="detail-status">{{ eventStatusLabel(detail.event.status) }}</dd></div>
        <div>
          <dt>Motif d'attente</dt>
          <dd data-testid="detail-reason">{{ holdReasonLabel(detail.event.holdReason) }}</dd>
        </div>
        <div><dt>Adhésion</dt><dd>{{ detail.event.contractNumber ?? '—' }}</dd></div>
        <div><dt>Date</dt><dd>{{ formatDate(detail.event.eventDate) }}</dd></div>
      </dl>
      <h3 class="ui-detail-section">Lignes produites</h3>
      <DataTable
        :columns="lineColumns"
        :rows="detail.lines"
        empty="Aucune ligne produite."
        empty-testid="no-produced-line"
        :row-testid="() => 'produced-line'"
      />
    </AppCard>
  </div>
</template>

<style scoped>
.ui-event-detail {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2) var(--space-5);
  margin: 0 0 var(--space-4);
}
.ui-event-detail div {
  display: flex;
  gap: var(--space-2);
}
.ui-event-detail dt {
  color: var(--c-text-muted);
  min-width: 9rem;
}
.ui-event-detail dd {
  margin: 0;
  font-weight: 500;
}
.ui-detail-section {
  margin: var(--space-4) 0 var(--space-2);
  font-size: var(--fs-h3);
}
</style>
