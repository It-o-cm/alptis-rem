<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { flowDirectionLabel } from '../presentation/reporting';
import { AppButton, AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface Flow {
  id: string;
  direction: string;
  system: string;
  content: string;
  format: string;
  frequency: string;
}

const FILTERS = [
  { key: '', label: 'Tous les flux' },
  { key: 'INBOUND', label: 'Entrants' },
  { key: 'OUTBOUND', label: 'Sortants' },
];

const rows = ref<Flow[]>([]);
const filter = ref('');
const error = ref('');
const loading = ref(false);

/** Columns of the flow catalog table. */
const columns: Column<Flow>[] = [
  { key: 'id', label: 'N°' },
  { key: 'direction', label: 'Sens' },
  { key: 'system', label: 'Système' },
  { key: 'content', label: 'Contenu' },
  { key: 'format', label: 'Format et transport' },
  { key: 'frequency', label: 'Fréquence' },
];

/**
 * French label of a flow direction.
 * @param d the direction code
 */
function directionLabel(d: string): string {
  return flowDirectionLabel(d);
}

/** Loads the flow catalog, filtered by the active direction. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    const q = filter.value ? `?direction=${filter.value}` : '';
    rows.value = await apiGet<Flow[]>(`/api/ui/v1/flows/catalog${q}`);
  } catch (e) {
    rows.value = [];
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/**
 * Selects a direction filter and reloads the catalog.
 * @param key the direction key to filter on
 */
function pick(key: string): void {
  filter.value = key;
  load();
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Catalogue des flux"
      subtitle="Tout ce que l'application reçoit et tout ce qu'elle émet : système, contenu, format, transport et fréquence (section 9.1)."
    />

    <AppCard>
      <div class="flux-filters">
        <AppButton
          v-for="f in FILTERS"
          :key="f.key"
          :variant="filter === f.key ? 'primary' : 'secondary'"
          :data-testid="`flux-filter-${f.key || 'all'}`"
          @click="pick(f.key)"
        >
          {{ f.label }}
        </AppButton>
      </div>
    </AppCard>

    <AppMessage v-if="error" variant="error" data-testid="flux-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        row-key="id"
        empty="Aucun flux."
        empty-testid="no-flow"
        :row-testid="(f) => `flow-row-${f.id}`"
      >
        <template #cell-id="{ row }"><span class="mono">{{ row.id }}</span></template>
        <template #cell-direction="{ row }">
          <span :data-testid="`flow-direction-${row.id}`">{{ directionLabel(row.direction) }}</span>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.flux-filters {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}
</style>
