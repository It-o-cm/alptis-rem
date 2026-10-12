<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { formatAmount } from '../presentation/format';
import { AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface PositionView {
  coverageCode: string;
  status: string | null;
  advanced: number;
  offset: number;
}

interface ConventionSummary {
  partnerCode: string | null;
  validFrom: string | null;
  validTo: string | null;
  advanced: number;
  offset: number;
  outstanding: number;
  positions: PositionView[];
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const rows = ref<ConventionSummary[]>([]);
const detail = ref<ConventionSummary | null>(null);
const error = ref('');
const loading = ref(false);

/**
 * Formats an amount in euros.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Columns of the per-convention discount synthesis (BRD-09). */
const columns: Column<ConventionSummary>[] = [
  { key: 'partnerCode', label: 'Distributeur', value: (c) => c.partnerCode ?? '—' },
  { key: 'validFrom', label: 'Du', value: (c) => c.validFrom ?? '—' },
  { key: 'validTo', label: 'Au', value: (c) => c.validTo ?? '—' },
  { key: 'advanced', label: 'Versé', align: 'num', value: (c) => amount(c.advanced) },
  { key: 'offset', label: 'Apuré', align: 'num', value: (c) => amount(c.offset) },
  { key: 'outstanding', label: 'Créance', align: 'num', value: (c) => amount(c.outstanding) },
];

/** Columns of the per-adherent (guarantee) detail of a convention. */
const positionColumns: Column<PositionView>[] = [
  { key: 'coverageCode', label: 'Garantie' },
  { key: 'status', label: 'Statut', value: (p) => p.status ?? '—' },
  { key: 'advanced', label: 'Versé', align: 'num', value: (p) => amount(p.advanced) },
  { key: 'offset', label: 'Apuré', align: 'num', value: (p) => amount(p.offset) },
];

/** Loads the discount synthesis of the current entity. */
async function load(): Promise<void> {
  error.value = '';
  detail.value = null;
  loading.value = true;
  try {
    rows.value = await apiGet<ConventionSummary[]>(`/api/ui/v1/discount-summary/${entity.value}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

/**
 * Shows the per-adherent detail of a convention.
 * @param row the selected convention
 */
function open(row: ConventionSummary): void {
  detail.value = row;
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Créances d'escompte"
      subtitle="Synthèse des escomptes par convention : versé, apuré, créance, et le détail par adhérent (BRD-09, CPT-08)."
    />

    <AppMessage v-if="error" variant="error" data-testid="discounts-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucune convention d'escompte."
        empty-testid="discounts-empty"
        clickable
        :row-testid="(c) => `discount-row-${c.partnerCode ?? 'na'}`"
        @row-click="open"
      />
    </AppCard>

    <AppCard v-if="detail" data-testid="discount-detail">
      <h2 class="disc-title">Détail par adhérent — {{ detail.partnerCode ?? '—' }}</h2>
      <DataTable
        :columns="positionColumns"
        :rows="detail.positions"
        empty="Aucune garantie escomptée."
        empty-testid="discount-no-position"
        :row-testid="() => 'discount-position-row'"
      />
    </AppCard>
  </div>
</template>

<style scoped>
.disc-title {
  margin: 0;
  font-size: var(--fs-h2);
}
</style>
