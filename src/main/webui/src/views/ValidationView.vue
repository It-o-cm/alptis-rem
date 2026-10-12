<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { periodStateLabel } from '../presentation/cycle';
import { lineNatureLabel } from '../presentation/lines';
import { formatAmount, formatPercent } from '../presentation/format';
import { AppButton, AppCard, AppMessage, DataTable, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface PeriodSummary {
  entityCode: string;
  entityName: string;
  month: string;
  state: string;
}

interface NatureTotal {
  nature: string;
  total: number;
}

interface ValidationSummary {
  month: string;
  state: string;
  currentTotal: number;
  previousTotal: number;
  gap: number;
  adjustmentCount: number;
  secondLevelRequired: boolean;
  firstLevelValidatedBy: string | null;
  byNature: NatureTotal[];
}

const session = useSessionStore();
const periods = ref<PeriodSummary[]>([]);
const selected = ref<PeriodSummary | null>(null);
const summary = ref<ValidationSummary | null>(null);
const error = ref('');
const message = ref('');
const busy = ref(false);

const canValidate = (): boolean => session.roles.includes('BUSINESS_VALIDATOR');

/** Columns of the per-nature breakdown. */
const natureColumns: Column<NatureTotal>[] = [
  { key: 'nature', label: 'Nature', value: (n) => lineNatureLabel(n.nature) },
  { key: 'total', label: 'Total', align: 'num', value: (n) => formatAmount(n.total) },
];

/** Loads the open periods, selecting the first. */
async function loadPeriods(): Promise<void> {
  error.value = '';
  try {
    periods.value = await apiGet<PeriodSummary[]>('/api/ui/v1/periods');
    if (periods.value.length > 0) {
      await select(periods.value[0]);
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

/** Selects a period and loads its validation summary. */
async function select(p: PeriodSummary): Promise<void> {
  selected.value = p;
  summary.value = await apiGet<ValidationSummary>(`/api/ui/v1/validation/${p.entityCode}/${p.month}`);
}

/** Gives a validation level on the selected period. */
async function validate(): Promise<void> {
  if (!selected.value) return;
  message.value = '';
  error.value = '';
  busy.value = true;
  try {
    summary.value = await apiPost<ValidationSummary>(
      `/api/ui/v1/validation/${selected.value.entityCode}/${selected.value.month}/validate`,
      {},
    );
    message.value = summary.value.state === 'BUSINESS_VALIDATED'
      ? 'Période validée par le métier.'
      : 'Premier niveau de validation enregistré ; un second validateur est requis.';
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Validation refusée';
  } finally {
    busy.value = false;
  }
}
onMounted(loadPeriods);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Validation métier"
      subtitle="Les totaux de la période, l'écart avec le mois précédent et un second niveau de validation quand une règle le demande (WKF-02)."
    />

    <AppMessage v-if="error" variant="error" data-testid="validation-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="validation-message">{{ message }}</AppMessage>

    <FilterBar>
      <FormField label="Période" width="short" v-slot="{ id }">
        <select
          :id="id"
          data-testid="validation-period"
          :value="selected ? `${selected.entityCode}|${selected.month}` : ''"
          @change="(e) => {
            const [c, m] = (e.target as HTMLSelectElement).value.split('|');
            const p = periods.find((x) => x.entityCode === c && x.month === m);
            if (p) select(p);
          }"
        >
          <option v-for="p in periods" :key="`${p.entityCode}|${p.month}`" :value="`${p.entityCode}|${p.month}`">
            {{ p.entityName }} — {{ p.month }} ({{ periodStateLabel(p.state) }})
          </option>
        </select>
      </FormField>
    </FilterBar>

    <AppCard v-if="summary" data-testid="validation-summary">
      <dl class="ui-validation">
        <div><dt>État</dt><dd data-testid="validation-state">{{ periodStateLabel(summary.state) }}</dd></div>
        <div><dt>Total de la période</dt><dd data-testid="validation-total">{{ formatAmount(summary.currentTotal) }}</dd></div>
        <div><dt>Mois précédent</dt><dd>{{ formatAmount(summary.previousTotal) }}</dd></div>
        <div><dt>Écart</dt><dd data-testid="validation-gap">{{ formatPercent(summary.gap) }}</dd></div>
        <div><dt>Ajustements</dt><dd>{{ summary.adjustmentCount }}</dd></div>
        <div>
          <dt>Second niveau</dt>
          <dd data-testid="validation-second">{{ summary.secondLevelRequired ? 'Requis' : 'Non requis' }}</dd>
        </div>
        <div v-if="summary.firstLevelValidatedBy">
          <dt>Premier validateur</dt>
          <dd data-testid="validation-first">{{ summary.firstLevelValidatedBy }}</dd>
        </div>
      </dl>

      <DataTable
        :columns="natureColumns"
        :rows="summary.byNature"
        empty="Aucune ligne."
        empty-testid="no-nature"
        :row-testid="(n) => `nature-row-${n.nature}`"
      />

      <AppButton
        v-if="canValidate() && summary.state === 'CONTROLLED'"
        :loading="busy"
        data-testid="validation-validate"
        @click="validate"
      >
        Valider les totaux
      </AppButton>
    </AppCard>
  </div>
</template>

<style scoped>
.ui-validation {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2) var(--space-5);
  margin: 0 0 var(--space-4);
}
.ui-validation div {
  display: flex;
  gap: var(--space-2);
}
.ui-validation dt {
  color: var(--c-text-muted);
  min-width: 11rem;
}
.ui-validation dd {
  margin: 0;
  font-weight: 500;
}
</style>
