<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { formatAmount } from '../presentation/format';
import BarChart from '../components/charts/BarChart.vue';
import {
  AppButton, AppCard, AppForm, AppMessage, DataTable, DistributorSelect, EntitySelect, FormField, MonthSelect,
  PageHeading, RuleSelect,
} from '../components/ui';
import type { Column } from '../components/ui';
import { lineNatureLabel } from '../presentation/lines';

interface NatureComparison {
  nature: string;
  real: number;
  simulated: number;
  ecart: number;
}

interface DistributorImpact {
  distributor: string;
  real: number;
  simulated: number;
  ecart: number;
}

interface Result {
  runId: number;
  realTotal: number;
  simulatedTotal: number;
  ecart: number;
  linesReplayed: number;
  linesModified: number;
  held: number;
  byNature: NatureComparison[];
  topDistributors: DistributorImpact[];
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const month = ref('2026-09-01');
const ruleCode = ref('');
const versionNo = ref(2);
const scopeDistributor = ref('');
const substitutions = ref<{ oldRate: string; newRate: string }[]>([{ oldRate: '', newRate: '' }]);
interface HistoryRow {
  periodMonth: string | null;
  ruleCode: string;
  versionNo: number | null;
  scopeDistributor: string | null;
  realTotal: number;
  simulatedTotal: number;
  ecart: number;
  linesReplayed: number | null;
  linesModified: number | null;
  held: number | null;
  runAt: string | null;
}

const result = ref<Result | null>(null);
const history = ref<HistoryRow[]>([]);
const error = ref('');

/** Whether the current user may run a simulation. */
const canRun = () => canEditEntityParameters(session.roles) || session.roles.includes('OPERATOR');

/**
 * Formats a monetary value in euros.
 * @param value the amount to format
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Appends an empty substitution row to the table. */
function addSubstitution(): void {
  substitutions.value.push({ oldRate: '', newRate: '' });
}

/**
 * Builds the data-testid of a touched-distributor row from its position.
 * @param d the distributor-impact row
 */
function distTestid(d: DistributorImpact): string {
  return `sim-dist-${result.value ? result.value.topDistributors.indexOf(d) : -1}`;
}

/** Nature labels used as the chart categories. */
const natureCategories = computed(() => (result.value ? result.value.byNature.map((n) => n.nature) : []));
/** Real and simulated series compared by nature. */
const compareSeries = computed(() => {
  if (!result.value) {
    return [];
  }
  return [
    { key: 'real', label: 'Réel', values: result.value.byNature.map((n) => n.real) },
    { key: 'simulated', label: 'Simulé', values: result.value.byNature.map((n) => n.simulated) },
  ];
});

/** Columns of the per-nature gap table. */
const natureColumns: Column<NatureComparison>[] = [
  { key: 'nature', label: 'Nature', value: (n) => lineNatureLabel(n.nature) },
  { key: 'real', label: 'Réel', align: 'num', value: (n) => amount(n.real) },
  { key: 'simulated', label: 'Simulé', align: 'num', value: (n) => amount(n.simulated) },
  { key: 'ecart', label: 'Écart', align: 'num', value: (n) => amount(n.ecart) },
];

/** Columns of the most-touched-distributors table. */
const distributorColumns: Column<DistributorImpact>[] = [
  { key: 'distributor', label: 'Distributeur' },
  { key: 'real', label: 'Réel', align: 'num', value: (d) => amount(d.real) },
  { key: 'simulated', label: 'Simulé', align: 'num', value: (d) => amount(d.simulated) },
  { key: 'ecart', label: 'Écart', align: 'num', value: (d) => amount(d.ecart) },
];

/** Columns of the simulation history table (PAR-13). */
const historyColumns: Column<HistoryRow>[] = [
  { key: 'runAt', label: 'Lancée le', value: (h) => h.runAt ?? '—' },
  { key: 'periodMonth', label: 'Période', value: (h) => h.periodMonth ?? '—' },
  { key: 'ruleCode', label: 'Règle', value: (h) => `${h.ruleCode} v${h.versionNo ?? '—'}` },
  { key: 'scopeDistributor', label: 'Périmètre', value: (h) => h.scopeDistributor ?? 'Tous' },
  { key: 'ecart', label: 'Écart', align: 'num', value: (h) => amount(h.ecart) },
  { key: 'linesModified', label: 'Modifiées', align: 'num', value: (h) => h.linesModified ?? '—' },
];

/** Loads the simulation history of the current entity. */
async function loadHistory(): Promise<void> {
  try {
    history.value = await apiGet<HistoryRow[]>(`/api/ui/v1/simulation/${entity.value}/history`);
  } catch {
    history.value = [];
  }
}

/** Runs the simulation for the chosen entity, period, rule and version. */
async function run(): Promise<void> {
  error.value = '';
  const table = substitutions.value
    .filter((s) => s.oldRate !== '')
    .map((s) => ({ oldRate: Number(s.oldRate), newRate: s.newRate === '' ? null : Number(s.newRate) }));
  try {
    result.value = await apiPost<Result>(`/api/ui/v1/simulation/${entity.value}/${month.value}`, {
      ruleCode: ruleCode.value,
      versionNo: versionNo.value,
      substitutions: table,
      scopeDistributor: scopeDistributor.value === '' ? null : scopeDistributor.value,
    });
    await loadHistory();
  } catch (e) {
    result.value = null;
    error.value = e instanceof ApiError ? e.message : 'Simulation impossible';
  }
}

onMounted(loadHistory);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Simulation d'une version de règle"
      subtitle="Rejoue une période passée avec une version en projet et compare au réel. Aucun effet sur les comptes, les bordereaux ni la comptabilité (PAR-13)."
    />

    <AppCard>
      <AppForm @submit="run">
        <FormField label="Entité" width="short" v-slot="{ id }">
          <EntitySelect :id="id" data-testid="sim-entity" v-model="entity" />
        </FormField>
        <FormField label="Période rejouée" width="short" v-slot="{ id }">
          <MonthSelect :id="id" data-testid="sim-month" v-model="month" :entity="entity" />
        </FormField>
        <FormField label="Règle" width="short" v-slot="{ id }">
          <RuleSelect :id="id" data-testid="sim-rule" v-model="ruleCode" allow-empty empty-label="Choisir une règle" />
        </FormField>
        <FormField label="Version" width="short" v-slot="{ id }">
          <input :id="id" data-testid="sim-version" type="number" v-model.number="versionNo" />
        </FormField>
        <FormField label="Distributeur (optionnel)" width="short" v-slot="{ id }">
          <DistributorSelect :id="id" data-testid="sim-scope" v-model="scopeDistributor" allow-empty />
        </FormField>
      </AppForm>

      <div class="sim-subs">
        <p class="sim-subs__label">Table de substitution (ancien taux → nouveau taux ; vide = retiré)</p>
        <div v-for="(s, i) in substitutions" :key="i" class="sim-subs__row">
          <input :data-testid="`sim-old-${i}`" class="sim-subs__input" aria-label="Ancien taux" type="text" v-model="s.oldRate" placeholder="0.30" />
          <span>→</span>
          <input :data-testid="`sim-new-${i}`" class="sim-subs__input" aria-label="Nouveau taux" type="text" v-model="s.newRate" placeholder="0.25" />
        </div>
        <AppButton variant="secondary" data-testid="sim-add-sub" @click="addSubstitution">Ajouter une substitution</AppButton>
      </div>

      <AppButton v-if="canRun()" data-testid="sim-run" @click="run">Lancer la simulation</AppButton>

      <AppMessage v-if="error" variant="error" data-testid="sim-error">{{ error }}</AppMessage>
    </AppCard>

    <AppCard v-if="!result" data-testid="sim-idle">
      Aucune simulation lancée. Choisir une règle, une version et une période, puis lancer.
    </AppCard>

    <template v-if="result">
      <div class="sim-tiles">
        <AppCard class="sim-tile">
          <div class="sim-tile__label">Réel</div>
          <div class="sim-tile__value" data-testid="sim-real">{{ amount(result.realTotal) }}</div>
        </AppCard>
        <AppCard class="sim-tile">
          <div class="sim-tile__label">Simulé</div>
          <div class="sim-tile__value" data-testid="sim-sim">{{ amount(result.simulatedTotal) }}</div>
        </AppCard>
        <AppCard class="sim-tile sim-tile--accent">
          <div class="sim-tile__label">Écart</div>
          <div class="sim-tile__value" data-testid="sim-ecart">{{ amount(result.ecart) }}</div>
        </AppCard>
        <AppCard class="sim-tile">
          <div class="sim-tile__label">Lignes modifiées</div>
          <div class="sim-tile__value" data-testid="sim-modified">{{ result.linesModified }}</div>
          <div class="sim-tile__sub">sur {{ result.linesReplayed }} rejouées</div>
          <div class="sim-tile__sub">Événements en attente : {{ result.held }}</div>
        </AppCard>
      </div>

      <AppCard>
        <BarChart mode="grouped" :categories="natureCategories" :series="compareSeries"
                  title="Réel contre simulé par nature" caption="en euros" testid="chart-sim" />
      </AppCard>

      <AppCard flush>
        <h2 class="sim-table-title">Écart par nature</h2>
        <DataTable :columns="natureColumns" :rows="result.byNature" :row-testid="(n) => `sim-nature-${n.nature}`">
          <template #cell-ecart="{ row }">
            <span :data-testid="`sim-nature-ecart-${row.nature}`">{{ amount(row.ecart) }}</span>
          </template>
        </DataTable>
      </AppCard>

      <AppCard v-if="result.topDistributors.length > 0" flush>
        <h2 class="sim-table-title">Distributeurs les plus touchés</h2>
        <DataTable :columns="distributorColumns" :rows="result.topDistributors" :row-testid="distTestid" />
      </AppCard>
    </template>

    <AppCard flush data-testid="sim-history">
      <h2 class="sim-table-title">Historique des simulations</h2>
      <DataTable
        :columns="historyColumns"
        :rows="history"
        empty="Aucune simulation enregistrée."
        empty-testid="sim-history-empty"
        :row-testid="() => 'sim-history-row'"
      />
    </AppCard>
  </div>
</template>

<style scoped>
.sim-subs__label {
  margin: 0 0 var(--space-2);
  font-size: var(--fs-small);
  font-weight: 600;
  color: var(--ink);
}
.sim-subs__row {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  margin-bottom: var(--space-2);
}
.sim-subs__input {
  width: var(--w-rate);
}
.sim-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-4);
}
.sim-tile__label {
  font-size: var(--fs-small);
  color: var(--muted);
}
.sim-tile__value {
  font-family: 'IBM Plex Mono', monospace;
  font-size: var(--fs-h2);
  font-weight: 500;
}
.sim-tile__sub {
  font-size: var(--fs-small);
  color: var(--muted);
}
.sim-tile--accent {
  border-color: var(--primary);
}
.sim-table-title {
  margin: 0;
  padding: var(--space-4) var(--space-4) var(--space-2);
  font-size: var(--fs-h3);
}
</style>
