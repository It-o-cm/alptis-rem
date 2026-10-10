<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppButton, AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface AuditRow {
  occurredAt: string;
  userId: string;
  entityCode: string | null;
  action: string;
  objectType: string;
  objectId: number | null;
  beforeValue: string | null;
  afterValue: string | null;
  previousHash: string;
  hash: string;
}

interface Page {
  items: AuditRow[];
  page: number;
  size: number;
  total: number;
}

const rows = ref<AuditRow[]>([]);
const page = ref(0);
const size = ref(20);
const total = ref(0);
const loading = ref(true);
const loadError = ref('');

/** Columns of the audit table. */
const columns: Column<AuditRow>[] = [
  { key: 'occurredAt', label: 'Date' },
  { key: 'userId', label: 'Auteur' },
  { key: 'entityCode', label: 'Entité' },
  { key: 'action', label: 'Action' },
  { key: 'objectType', label: 'Objet' },
  { key: 'beforeValue', label: 'Avant' },
  { key: 'afterValue', label: 'Après' },
  { key: 'hash', label: 'Empreinte' },
];

/**
 * Shortens a hash for display, keeping the first twelve characters.
 * @param hash the full hash, or null
 */
function shortHash(hash: string | null): string {
  return hash ? hash.substring(0, 12) + '…' : '';
}

/** Loads the current page of the audit trail. */
async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const result = await apiGet<Page>(`/api/ui/v1/audit?page=${page.value}&size=${size.value}`);
    rows.value = result.items;
    total.value = result.total;
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

/** Moves to the next page when there is one. */
function next() {
  if ((page.value + 1) * size.value < total.value) {
    page.value += 1;
    reload();
  }
}

/** Moves to the previous page when there is one. */
function previous() {
  if (page.value > 0) {
    page.value -= 1;
    reload();
  }
}

onMounted(reload);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Piste d'audit"
      subtitle="Toute modification manuelle : auteur, date, avant, après, chaînée par empreinte (ADM-04)."
    />

    <AppMessage v-if="loadError" variant="error" data-testid="load-error">{{ loadError }}</AppMessage>

    <AppCard v-else>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucune trace."
        empty-testid="empty"
        :row-testid="() => 'audit-row'"
      >
        <template #cell-userId="{ row }"><span data-testid="audit-user">{{ row.userId }}</span></template>
        <template #cell-entityCode="{ row }"><span data-testid="audit-entity">{{ row.entityCode ?? '—' }}</span></template>
        <template #cell-action="{ row }"><span data-testid="audit-action">{{ row.action }}</span></template>
        <template #cell-objectType="{ row }">{{ row.objectType }} #{{ row.objectId }}</template>
        <template #cell-beforeValue="{ row }"><span class="audit-cell">{{ row.beforeValue ?? '—' }}</span></template>
        <template #cell-afterValue="{ row }"><span class="audit-cell">{{ row.afterValue ?? '—' }}</span></template>
        <template #cell-hash="{ row }"><span class="mono" :title="row.hash">{{ shortHash(row.hash) }}</span></template>
      </DataTable>
      <div class="audit-pager">
        <AppButton variant="secondary" data-testid="audit-prev" :disabled="page === 0" @click="previous">Précédent</AppButton>
        <span data-testid="audit-page">Page {{ page + 1 }} · {{ total }} trace(s)</span>
        <AppButton variant="secondary" data-testid="audit-next" :disabled="(page + 1) * size >= total" @click="next">Suivant</AppButton>
      </div>
    </AppCard>
  </div>
</template>

<style scoped>
.audit-cell {
  display: inline-block;
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.audit-pager {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}
</style>
