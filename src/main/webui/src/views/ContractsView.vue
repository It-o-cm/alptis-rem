<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppButton, AppCard, AppMessage, DataTable, DetailList, DetailRow, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';
import { contractStatusLabel, remunerationModeLabel } from '../presentation/coverage';
import { eventTypeLabel, eventStatusLabel } from '../presentation/events';
import { lineNatureLabel, lineStatusLabel } from '../presentation/lines';
import { advanceStatusLabel } from '../presentation/advance';

interface CoverageView {
  coverageCode: string;
  status: string | null;
  annualPremium: string | null;
}

interface ContractView {
  entityCode: string;
  contractNumber: string;
  sourceSystem: string;
  status: string | null;
  coverages: CoverageView[];
}

interface AssignmentView {
  productionCode: string | null; role: string | null; share: string | null;
  origin: string | null; validFrom: string | null; validTo: string | null;
}

interface ChoiceView {
  mode: string | null; linearRate: string | null; acquisitionRate: string | null;
  servicingRate: string | null; advanceRequested: boolean; validFrom: string | null; validTo: string | null;
}

interface EventRefView { eventType: string | null; eventDate: string | null; status: string | null; }
interface LineRefView { nature: string | null; amount: number; status: string | null; }
interface PositionRefView { status: string | null; advanced: string; offset: string; }

interface CoverageDetail {
  coverageCode: string; contractNumber: string; status: string | null; effectDate: string | null;
  endDate: string | null; annualPremium: string | null; assignments: AssignmentView[]; choices: ChoiceView[];
  events: EventRefView[]; lines: LineRefView[]; position: PositionRefView | null;
}

const term = ref('');
const results = ref<ContractView[]>([]);
const detail = ref<CoverageDetail | null>(null);
const error = ref('');
const loading = ref(false);

/** Columns of the contracts results table. */
const columns: Column<ContractView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'contractNumber', label: 'Adhésion' },
  { key: 'sourceSystem', label: 'Source' },
  { key: 'status', label: 'Statut', value: (c) => contractStatusLabel(c.status) },
  { key: 'coverages', label: 'Garanties' },
];

/** Searches contracts by membership number within the user's entities. */
async function search(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    const page = await apiGet<{ items: ContractView[] }>(`/api/ui/v1/contracts?q=${encodeURIComponent(term.value)}`);
    results.value = page.items;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de recherche';
  } finally {
    loading.value = false;
  }
}

/** Clears the search term and its results. */
function reset(): void {
  term.value = '';
  results.value = [];
  detail.value = null;
  error.value = '';
}

/**
 * Opens the fiche of a guarantee (affectations, remuneration choice, events, lines, discount).
 * @param contract the contract holding the guarantee
 * @param coverageCode the guarantee code
 */
async function openCoverage(contract: ContractView, coverageCode: string): Promise<void> {
  error.value = '';
  try {
    detail.value = await apiGet<CoverageDetail>(
      `/api/ui/v1/contracts/${contract.entityCode}/${contract.contractNumber}/coverages/${coverageCode}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

/** Columns of the affectations table. */
const assignmentColumns: Column<AssignmentView>[] = [
  { key: 'productionCode', label: 'Code de production', value: (a) => a.productionCode ?? '—' },
  { key: 'role', label: 'Rôle', value: (a) => a.role ?? '—' },
  { key: 'share', label: 'Part', value: (a) => a.share ?? '—' },
  { key: 'origin', label: 'Origine', value: (a) => a.origin ?? '—' },
  { key: 'validFrom', label: 'Depuis', value: (a) => a.validFrom ?? '—' },
  { key: 'validTo', label: "Jusqu'à", value: (a) => a.validTo ?? '—' },
];

/** Columns of the remuneration-choices table. */
const choiceColumns: Column<ChoiceView>[] = [
  { key: 'mode', label: 'Mode', value: (c) => remunerationModeLabel(c.mode) },
  { key: 'linearRate', label: 'Taux linéaire', value: (c) => c.linearRate ?? '—' },
  { key: 'acquisitionRate', label: "Taux d'apport", value: (c) => c.acquisitionRate ?? '—' },
  { key: 'servicingRate', label: 'Taux de suivi', value: (c) => c.servicingRate ?? '—' },
  { key: 'advanceRequested', label: 'Escompte', value: (c) => (c.advanceRequested ? 'Oui' : 'Non') },
];

/** Columns of the events table. */
const eventColumns: Column<EventRefView>[] = [
  { key: 'eventType', label: 'Type', value: (e) => eventTypeLabel(e.eventType) },
  { key: 'eventDate', label: 'Date', value: (e) => e.eventDate ?? '—' },
  { key: 'status', label: 'Statut', value: (e) => eventStatusLabel(e.status) },
];

/** Columns of the commission-lines table. */
const lineColumns: Column<LineRefView>[] = [
  { key: 'nature', label: 'Nature', value: (l) => lineNatureLabel(l.nature) },
  { key: 'amount', label: 'Montant', align: 'num' },
  { key: 'status', label: 'Statut', value: (l) => lineStatusLabel(l.status) },
];

onMounted(search);
</script>

<template>
  <div class="page">
    <PageHeading title="Recherche contrats" subtitle="Adhésions et garanties, dans vos entités." />

    <FilterBar :loading="loading" search-testid="search-button" @search="search" @reset="reset">
      <FormField label="Numéro d'adhésion" stacked width="long" v-slot="{ id }">
        <input :id="id" data-testid="search-term" type="text" v-model="term" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="search-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="results"
        :loading="loading"
        :row-key="(c) => c.entityCode + c.sourceSystem + c.contractNumber"
        empty="Aucune adhésion."
        empty-testid="empty"
        :row-testid="(c) => `contract-row-${c.contractNumber}`"
      >
        <template #cell-contractNumber="{ row }"><span class="mono">{{ row.contractNumber }}</span></template>
        <template #cell-coverages="{ row }">
          <AppButton
            v-for="cov in row.coverages"
            :key="cov.coverageCode"
            variant="secondary"
            class="ui-cov"
            :data-testid="`coverage-${row.contractNumber}-${cov.coverageCode}`"
            @click="openCoverage(row, cov.coverageCode)"
          >
            {{ cov.coverageCode }}<span v-if="cov.annualPremium"> · {{ cov.annualPremium }}</span>
          </AppButton>
          <span v-if="row.coverages.length === 0">—</span>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="coverage-detail">
      <h2 class="cov-title">Adhésion {{ detail.contractNumber }} — garantie {{ detail.coverageCode }}</h2>
      <DetailList>
        <DetailRow label="Statut">{{ contractStatusLabel(detail.status) }}</DetailRow>
        <DetailRow label="Effet">{{ detail.effectDate ?? '—' }}</DetailRow>
        <DetailRow label="Fin">{{ detail.endDate ?? '—' }}</DetailRow>
        <DetailRow label="Prime annualisée">{{ detail.annualPremium ?? '—' }}</DetailRow>
      </DetailList>

      <h3 class="cov-sub">Affectations</h3>
      <DataTable :columns="assignmentColumns" :rows="detail.assignments" empty="Aucune affectation."
                 empty-testid="cov-no-assignment" :row-testid="() => 'assignment-row'" />

      <h3 class="cov-sub">Choix de rémunération</h3>
      <DataTable :columns="choiceColumns" :rows="detail.choices" empty="Aucun choix de rémunération."
                 empty-testid="cov-no-choice" :row-testid="() => 'choice-row'" />

      <h3 class="cov-sub">Événements</h3>
      <DataTable :columns="eventColumns" :rows="detail.events" empty="Aucun événement."
                 empty-testid="cov-no-event" :row-testid="() => 'event-row'" />

      <h3 class="cov-sub">Lignes de commission</h3>
      <DataTable :columns="lineColumns" :rows="detail.lines" empty="Aucune ligne."
                 empty-testid="cov-no-line" :row-testid="() => 'cov-line-row'" />

      <h3 class="cov-sub">Position d'escompte</h3>
      <DetailList v-if="detail.position" data-testid="cov-position">
        <DetailRow label="Statut">{{ advanceStatusLabel(detail.position.status) }}</DetailRow>
        <DetailRow label="Escompté">{{ detail.position.advanced }}</DetailRow>
        <DetailRow label="Apuré">{{ detail.position.offset }}</DetailRow>
      </DetailList>
      <AppMessage v-else variant="info" data-testid="cov-no-position">Aucune position d'escompte.</AppMessage>
    </AppCard>
  </div>
</template>

<style scoped>
.ui-cov {
  margin-right: var(--space-1);
}
.cov-title {
  margin: 0;
  font-size: var(--fs-h2);
}
.cov-sub {
  margin: 0;
  font-size: var(--fs-h3);
  font-weight: 600;
}
</style>
