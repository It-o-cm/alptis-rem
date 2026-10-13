<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { familyLabel, versionStatusClass, conflictLabel, schemaFields, repriseLabel, conditionsLabel } from '../presentation/rules';
import type { SchemaField, ConditionView } from '../presentation/rules';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface PlanView { entityCode: string; code: string; label: string; direction: string | null; vintage: number | null; }
interface RuleView { code: string; label: string; family: string | null; }
interface VersionView {
  versionNo: number; validFrom: string | null; validTo: string | null; status: string | null;
  priority: number | null; exclusionGroup: string | null; parameters: string | null;
  clawbackPolicy: string | null; paymentSchedule: string | null; conditions: ConditionView[];
  approvedBy: string | null; conflicts: string[];
}
interface FamilyView { code: string; label: string; }

const session = useSessionStore();
const entityCode = () => (session.entities.length > 0 ? session.entities[0] : '');
const canEdit = () => canEditEntityParameters(session.roles);

const plans = ref<PlanView[]>([]);
const families = ref<FamilyView[]>([]);
const rules = ref<RuleView[]>([]);
const versions = ref<VersionView[]>([]);
const selectedPlan = ref<string | null>(null);
const selectedRule = ref<string | null>(null);
const selectedFamily = ref<string | null>(null);
const schema = ref<SchemaField[]>([]);
const error = ref('');
const message = ref('');

interface ConditionRow { indicatorType: string; counterCode: string; operator: string; threshold: number; measurementPeriod: string; onFailure: string; }

const planForm = ref({ code: '', label: '', direction: 'INBOUND', vintage: 2026 });
const dupForm = ref({ newCode: '', newVintage: 2027 });
const ruleForm = ref({ code: '', label: '', family: 'LINEAR' });
const versionForm = ref({
  validFrom: '', validTo: '', versionAnchor: 'EVENT_DATE', scope: '{"product":[""]}',
  basis: 'COLLECTED_PREMIUM', outputNature: 'LINEAR', parameters: '{"allowedRates":[0.15]}',
  priority: 10, exclusionGroup: 'BASE', clawbackPolicy: '', paymentSchedule: '',
});
const conditionRows = ref<ConditionRow[]>([]);

/** Adds an empty payment-condition row to the version form (PAR-07). */
function addConditionRow(): void {
  conditionRows.value.push({
    indicatorType: '', counterCode: '', operator: 'LT', threshold: 0, measurementPeriod: '', onFailure: 'DEFER',
  });
}

/**
 * Removes the payment-condition row at the given index.
 * @param index the row index to remove
 */
function removeConditionRow(index: number): void {
  conditionRows.value.splice(index, 1);
}

/** Columns of the remuneration plans table. */
const planColumns: Column<PlanView>[] = [
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'direction', label: 'Sens', value: (p) => p.direction ?? '—' },
  { key: 'vintage', label: 'Millésime', value: (p) => p.vintage ?? '—' },
];

/** Columns of the plan's rules table. */
const ruleColumns: Column<RuleView>[] = [
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'family', label: 'Famille', value: (r) => familyLabel(r.family) },
];

/** Columns of the rule's versions table. */
const versionColumns: Column<VersionView>[] = [
  { key: 'versionNo', label: 'Version' },
  { key: 'validity', label: 'Validité', value: (v) => `${v.validFrom ?? '—'} → ${v.validTo ?? '—'}` },
  { key: 'status', label: 'Statut' },
  { key: 'priority', label: 'Priorité', align: 'num', value: (v) => v.priority ?? '—' },
  { key: 'exclusionGroup', label: 'Groupe', value: (v) => v.exclusionGroup ?? '—' },
  { key: 'reprise', label: 'Reprise', value: (v) => repriseLabel(v.clawbackPolicy, v.paymentSchedule) },
  { key: 'conditions', label: 'Conditions', value: (v) => conditionsLabel(v.conditions) },
  { key: 'conflicts', label: 'Conflits' },
];

/**
 * Records a failure, showing its message and clearing any success.
 * @param e the caught error
 */
function fail(e: unknown): void {
  error.value = e instanceof ApiError ? e.message : 'Erreur';
  message.value = '';
}

/**
 * Records a success message, clearing any error.
 * @param text the success message to show
 */
function ok(text: string): void {
  message.value = text;
  error.value = '';
}

/** Loads the remuneration plans and calculation families. */
async function loadPlans(): Promise<void> {
  try {
    plans.value = await apiGet<PlanView[]>('/api/ui/v1/plans');
    families.value = await apiGet<FamilyView[]>('/api/ui/v1/families');
  } catch (e) {
    fail(e);
  }
}

/** Creates a plan from the plan form, then reloads the plans. */
async function createPlan(): Promise<void> {
  try {
    await apiPost('/api/ui/v1/plans', { entityCode: entityCode(), ...planForm.value });
    ok('Plan créé.');
    await loadPlans();
  } catch (e) {
    fail(e);
  }
}

/**
 * Opens a plan and loads its rules.
 * @param code the plan code to open
 */
async function openPlan(code: string): Promise<void> {
  selectedPlan.value = code;
  selectedRule.value = null;
  versions.value = [];
  try {
    rules.value = await apiGet<RuleView[]>(`/api/ui/v1/plans/${code}/rules?entityCode=${entityCode()}`);
  } catch (e) {
    fail(e);
  }
}

/** Duplicates the selected plan under a new code and vintage. */
async function duplicatePlan(): Promise<void> {
  if (selectedPlan.value === null) {
    return;
  }
  try {
    await apiPost(`/api/ui/v1/plans/${selectedPlan.value}/duplicate`,
      { entityCode: entityCode(), newCode: dupForm.value.newCode, newVintage: dupForm.value.newVintage });
    ok('Plan dupliqué.');
    await loadPlans();
  } catch (e) {
    fail(e);
  }
}

/** Creates a rule in the selected plan, then reopens the plan. */
async function createRule(): Promise<void> {
  if (selectedPlan.value === null) {
    return;
  }
  try {
    await apiPost(`/api/ui/v1/plans/${selectedPlan.value}/rules`, { entityCode: entityCode(), ...ruleForm.value });
    ok('Règle créée.');
    await openPlan(selectedPlan.value);
  } catch (e) {
    fail(e);
  }
}

/**
 * Opens a rule, loads its versions and fetches the JSON Schema of its family so the form can guide
 * the parameters (PAR-02, PAR-06).
 * @param code the rule code to open
 */
async function openRule(code: string): Promise<void> {
  selectedRule.value = code;
  const rule = rules.value.find((r) => r.code === code);
  selectedFamily.value = rule?.family ?? null;
  schema.value = [];
  if (selectedFamily.value !== null) {
    try {
      const raw = await apiGet<unknown>(`/api/ui/v1/families/${selectedFamily.value}/schema`);
      schema.value = schemaFields(JSON.stringify(raw));
    } catch (e) {
      fail(e);
    }
  }
  await loadVersions();
}

/** Loads the versions of the selected rule. */
async function loadVersions(): Promise<void> {
  if (selectedPlan.value === null || selectedRule.value === null) {
    return;
  }
  try {
    versions.value = await apiGet<VersionView[]>(
      `/api/ui/v1/plans/${selectedPlan.value}/rules/${selectedRule.value}/versions?entityCode=${entityCode()}`);
  } catch (e) {
    fail(e);
  }
}

/** Adds a draft version to the selected rule from the version form. */
async function addVersion(): Promise<void> {
  if (selectedPlan.value === null || selectedRule.value === null) {
    return;
  }
  try {
    await apiPost(
      `/api/ui/v1/plans/${selectedPlan.value}/rules/${selectedRule.value}/versions`,
      {
        entityCode: entityCode(),
        validFrom: versionForm.value.validFrom || null,
        validTo: versionForm.value.validTo || null,
        versionAnchor: versionForm.value.versionAnchor,
        scope: versionForm.value.scope,
        triggerEventTypes: ['PREMIUM_COLLECTED'],
        basis: versionForm.value.basis,
        outputNature: versionForm.value.outputNature,
        parameters: versionForm.value.parameters,
        clawbackPolicy: versionForm.value.clawbackPolicy || null,
        paymentSchedule: versionForm.value.paymentSchedule || null,
        conditions: conditionRows.value.map((c) => ({
          indicatorType: c.indicatorType || null,
          counterCode: c.counterCode || null,
          operator: c.operator || null,
          threshold: c.threshold,
          measurementPeriod: c.measurementPeriod || null,
          onFailure: c.onFailure || null,
        })),
        priority: versionForm.value.priority,
        exclusionGroup: versionForm.value.exclusionGroup,
      });
    ok('Version créée (projet).');
    conditionRows.value = [];
    await loadVersions();
  } catch (e) {
    fail(e);
  }
}

/**
 * Generates the amendment (avenant) of a version and sends it for signature (PAR-11).
 * @param versionNo the version number
 */
async function generateAmendment(versionNo: number): Promise<void> {
  if (selectedPlan.value === null || selectedRule.value === null) {
    return;
  }
  try {
    const amendment = await apiPost<{ reference: string; signatureStatus: string }>(
      `/api/ui/v1/plans/${selectedPlan.value}/rules/${selectedRule.value}/versions/${versionNo}/amendment`
      + `?entityCode=${entityCode()}`, {});
    ok(`Avenant ${amendment.reference} généré et envoyé en signature (${amendment.signatureStatus}).`);
  } catch (e) {
    fail(e);
  }
}

/**
 * Approves a draft version of the selected rule.
 * @param versionNo the version number to approve
 */
async function approve(versionNo: number): Promise<void> {
  if (selectedPlan.value === null || selectedRule.value === null) {
    return;
  }
  try {
    await apiPost(
      `/api/ui/v1/plans/${selectedPlan.value}/rules/${selectedRule.value}/versions/${versionNo}/approve`
      + `?entityCode=${entityCode()}`, {});
    ok('Version approuvée.');
    await loadVersions();
  } catch (e) {
    fail(e);
  }
}

onMounted(loadPlans);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Paramétrage des règles"
      subtitle="Plans, règles et versions, avec leur cycle d'approbation (PAR-01 à PAR-12)."
    />

    <AppMessage v-if="error" variant="error" data-testid="rules-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="rules-message">{{ message }}</AppMessage>

    <AppCard title="Plans de rémunération">
      <DataTable :columns="planColumns" :rows="plans" :row-key="(p) => p.entityCode + p.code"
                 empty="Aucun plan." empty-testid="plans-empty" :row-testid="(p) => `plan-row-${p.code}`">
        <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`plan-open-${row.code}`" @click="openPlan(row.code)">
            Ouvrir
          </AppButton>
        </template>
      </DataTable>

      <AppForm v-if="canEdit()" @submit="createPlan">
        <FormField label="Code" width="short" v-slot="{ id }">
          <input :id="id" data-testid="plan-code" v-model="planForm.code" />
        </FormField>
        <FormField label="Libellé" width="long" v-slot="{ id }">
          <input :id="id" data-testid="plan-label" v-model="planForm.label" />
        </FormField>
        <FormField label="Sens" width="short" v-slot="{ id }">
          <select :id="id" data-testid="plan-direction" v-model="planForm.direction">
            <option value="INBOUND">INBOUND</option><option value="OUTBOUND">OUTBOUND</option>
          </select>
        </FormField>
        <FormField label="Millésime" width="short" v-slot="{ id }">
          <input :id="id" data-testid="plan-vintage" type="number" v-model.number="planForm.vintage" />
        </FormField>
        <AppButton type="submit" data-testid="plan-create">Créer le plan</AppButton>
      </AppForm>
    </AppCard>

    <AppCard v-if="selectedPlan" data-testid="rules-section" :title="`Règles du plan ${selectedPlan}`">
      <DataTable :columns="ruleColumns" :rows="rules" row-key="code"
                 empty="Aucune règle." empty-testid="rules-empty" :row-testid="(r) => `rule-row-${r.code}`">
        <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`rule-open-${row.code}`" @click="openRule(row.code)">
            Versions
          </AppButton>
        </template>
      </DataTable>

      <AppForm v-if="canEdit()" @submit="createRule">
        <FormField label="Code" width="short" v-slot="{ id }">
          <input :id="id" data-testid="rule-code" v-model="ruleForm.code" />
        </FormField>
        <FormField label="Libellé" width="long" v-slot="{ id }">
          <input :id="id" data-testid="rule-label" v-model="ruleForm.label" />
        </FormField>
        <FormField label="Famille" width="short" v-slot="{ id }">
          <select :id="id" data-testid="rule-family" v-model="ruleForm.family">
            <option v-for="f in families" :key="f.code" :value="f.code">{{ f.label }}</option>
          </select>
        </FormField>
        <AppButton variant="secondary" type="submit" data-testid="rule-create">Créer la règle</AppButton>
      </AppForm>

      <AppForm v-if="canEdit()" @submit="duplicatePlan">
        <FormField label="Nouveau code" width="short" v-slot="{ id }">
          <input :id="id" data-testid="dup-code" v-model="dupForm.newCode" />
        </FormField>
        <FormField label="Nouveau millésime" width="short" v-slot="{ id }">
          <input :id="id" data-testid="dup-vintage" type="number" v-model.number="dupForm.newVintage" />
        </FormField>
        <AppButton variant="secondary" type="submit" data-testid="plan-duplicate">Dupliquer le plan</AppButton>
      </AppForm>
    </AppCard>

    <AppCard v-if="selectedRule" data-testid="versions-section" :title="`Versions et conflits — ${selectedRule}`">
      <DataTable :columns="versionColumns" :rows="versions" row-key="versionNo"
                 empty="Aucune version." empty-testid="versions-empty" :row-testid="(v) => `version-row-${v.versionNo}`">
        <template #cell-status="{ row }">
          <span :class="versionStatusClass(row.status)" data-testid="version-status">{{ row.status ?? '—' }}</span>
        </template>
        <template #cell-conflicts="{ row }">
          <span data-testid="version-conflicts">{{ conflictLabel(row.conflicts) }}</span>
        </template>
        <template #actions="{ row }">
          <AppButton v-if="canEdit() && row.status === 'DRAFT'" variant="secondary"
                     :data-testid="`version-approve-${row.versionNo}`" @click="approve(row.versionNo)">
            Approuver
          </AppButton>
          <AppButton v-if="canEdit()" variant="secondary"
                     :data-testid="`version-amendment-${row.versionNo}`" @click="generateAmendment(row.versionNo)">
            Générer l'avenant
          </AppButton>
        </template>
      </DataTable>

      <p v-if="schema.length > 0" class="schema-hint" data-testid="family-schema">
        Paramètres attendus de la famille {{ familyLabel(selectedFamily) }} :
        <span v-for="f in schema" :key="f.name" class="tag" :data-testid="`schema-field-${f.name}`">
          {{ f.name }} ({{ f.type }}){{ f.required ? ' *' : '' }}
        </span>
      </p>

      <AppForm v-if="canEdit()" two-column @submit="addVersion">
        <FormField label="Valable du" width="date" v-slot="{ id }">
          <input :id="id" data-testid="v-from" type="date" v-model="versionForm.validFrom" />
        </FormField>
        <FormField label="Au" width="date" v-slot="{ id }">
          <input :id="id" data-testid="v-to" type="date" v-model="versionForm.validTo" />
        </FormField>
        <FormField label="Périmètre (JSON)" width="long" v-slot="{ id }">
          <input :id="id" data-testid="v-scope" v-model="versionForm.scope" />
        </FormField>
        <FormField label="Paramètres (JSON)" width="long" v-slot="{ id }">
          <input :id="id" data-testid="v-parameters" v-model="versionForm.parameters" />
        </FormField>
        <FormField label="Priorité" width="short" v-slot="{ id }">
          <input :id="id" data-testid="v-priority" type="number" v-model.number="versionForm.priority" />
        </FormField>
        <FormField label="Groupe d'exclusion" width="short" v-slot="{ id }">
          <input :id="id" data-testid="v-group" v-model="versionForm.exclusionGroup" />
        </FormField>
        <FormField label="Politique de reprise (JSON)" width="long" v-slot="{ id }">
          <input :id="id" data-testid="v-clawback" v-model="versionForm.clawbackPolicy"
                 placeholder='{"mode":"PRORATA"}' />
        </FormField>
        <FormField label="Échéancier de paiement (JSON)" width="long" v-slot="{ id }">
          <input :id="id" data-testid="v-schedule" v-model="versionForm.paymentSchedule"
                 placeholder='{"months":12}' />
        </FormField>
        <div v-for="(c, i) in conditionRows" :key="i" class="condition-row" :data-testid="`condition-row-${i}`">
          <FormField label="Indicateur" width="short" v-slot="{ id }">
            <input :id="id" :data-testid="`c-indicator-${i}`" v-model="c.indicatorType" />
          </FormField>
          <FormField label="Compteur" width="short" v-slot="{ id }">
            <input :id="id" :data-testid="`c-counter-${i}`" v-model="c.counterCode" />
          </FormField>
          <FormField label="Opérateur" width="short" v-slot="{ id }">
            <select :id="id" :data-testid="`c-operator-${i}`" v-model="c.operator">
              <option value="LT">LT</option><option value="LE">LE</option><option value="GT">GT</option>
              <option value="GE">GE</option><option value="EQ">EQ</option>
            </select>
          </FormField>
          <FormField label="Seuil" width="short" v-slot="{ id }">
            <input :id="id" :data-testid="`c-threshold-${i}`" type="number" step="any" v-model.number="c.threshold" />
          </FormField>
          <FormField label="Période" width="short" v-slot="{ id }">
            <input :id="id" :data-testid="`c-period-${i}`" v-model="c.measurementPeriod" />
          </FormField>
          <FormField label="En cas d'échec" width="short" v-slot="{ id }">
            <select :id="id" :data-testid="`c-failure-${i}`" v-model="c.onFailure">
              <option value="FORFEIT">Perte</option><option value="DEFER">Report</option>
            </select>
          </FormField>
          <AppButton variant="secondary" type="button" :data-testid="`c-remove-${i}`" @click="removeConditionRow(i)">
            Retirer
          </AppButton>
        </div>
        <AppButton variant="secondary" type="button" data-testid="condition-add" @click="addConditionRow">
          Ajouter une condition
        </AppButton>
        <AppButton variant="secondary" type="submit" data-testid="version-create">Ajouter la version</AppButton>
      </AppForm>
    </AppCard>
  </div>
</template>

<style scoped>
.schema-hint {
  margin: var(--space-3) 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}
.condition-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: flex-end;
  padding: var(--space-3);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  grid-column: 1 / -1;
}
</style>
