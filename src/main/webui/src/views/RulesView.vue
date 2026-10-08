<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { familyLabel, versionStatusClass, conflictLabel } from '../presentation/rules';

interface PlanView { entityCode: string; code: string; label: string; direction: string | null; vintage: number | null; }
interface RuleView { code: string; label: string; family: string | null; }
interface VersionView {
  versionNo: number; validFrom: string | null; validTo: string | null; status: string | null;
  priority: number | null; exclusionGroup: string | null; parameters: string | null; approvedBy: string | null;
  conflicts: string[];
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
const error = ref('');
const message = ref('');

const planForm = ref({ code: '', label: '', direction: 'INBOUND', vintage: 2026 });
const dupForm = ref({ newCode: '', newVintage: 2027 });
const ruleForm = ref({ code: '', label: '', family: 'LINEAR' });
const versionForm = ref({
  validFrom: '', validTo: '', versionAnchor: 'EVENT_DATE', scope: '{"product":[""]}',
  basis: 'COLLECTED_PREMIUM', outputNature: 'LINEAR', parameters: '{"allowedRates":[0.15]}',
  priority: 10, exclusionGroup: 'BASE',
});

function fail(e: unknown) {
  error.value = e instanceof ApiError ? e.message : 'Erreur';
  message.value = '';
}
function ok(text: string) {
  message.value = text;
  error.value = '';
}

async function loadPlans() {
  try {
    plans.value = await apiGet<PlanView[]>('/api/ui/v1/plans');
    families.value = await apiGet<FamilyView[]>('/api/ui/v1/families');
  } catch (e) {
    fail(e);
  }
}

async function createPlan() {
  try {
    await apiPost('/api/ui/v1/plans', { entityCode: entityCode(), ...planForm.value });
    ok('Plan créé.');
    await loadPlans();
  } catch (e) {
    fail(e);
  }
}

async function openPlan(code: string) {
  selectedPlan.value = code;
  selectedRule.value = null;
  versions.value = [];
  try {
    rules.value = await apiGet<RuleView[]>(`/api/ui/v1/plans/${code}/rules?entityCode=${entityCode()}`);
  } catch (e) {
    fail(e);
  }
}

async function duplicatePlan() {
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

async function createRule() {
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

async function openRule(code: string) {
  selectedRule.value = code;
  await loadVersions();
}

async function loadVersions() {
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

async function addVersion() {
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
        priority: versionForm.value.priority,
        exclusionGroup: versionForm.value.exclusionGroup,
      });
    ok('Version créée (projet).');
    await loadVersions();
  } catch (e) {
    fail(e);
  }
}

async function approve(versionNo: number) {
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
    <div>
      <h1 class="page__title" data-testid="page-title">Paramétrage des règles</h1>
      <div class="page__subtitle">Plans, règles et versions, avec leur cycle d'approbation (PAR-01 à PAR-12).</div>
    </div>
    <div v-if="error" class="card" style="padding: 12px 16px; color: #8f1d16" data-testid="rules-error">{{ error }}</div>
    <div v-if="message" class="card" style="padding: 12px 16px; color: #1d6b2f" data-testid="rules-message">{{ message }}</div>

    <section class="card" style="overflow-x: auto">
      <h2 style="margin: 12px 16px">Plans de rémunération</h2>
      <table>
        <thead><tr><th>Code</th><th>Libellé</th><th>Sens</th><th>Millésime</th><th></th></tr></thead>
        <tbody>
          <tr v-if="plans.length === 0"><td colspan="5" data-testid="plans-empty">Aucun plan.</td></tr>
          <tr v-for="p in plans" :key="p.entityCode + p.code" :data-testid="`plan-row-${p.code}`">
            <td style="font-family: 'IBM Plex Mono', monospace">{{ p.code }}</td>
            <td>{{ p.label }}</td>
            <td>{{ p.direction ?? '—' }}</td>
            <td>{{ p.vintage ?? '—' }}</td>
            <td><button class="btn" :data-testid="`plan-open-${p.code}`" @click="openPlan(p.code)">Ouvrir</button></td>
          </tr>
        </tbody>
      </table>
      <div v-if="canEdit()" style="padding: 12px 16px; display: flex; gap: 8px; flex-wrap: wrap; align-items: end">
        <label>Code<input data-testid="plan-code" v-model="planForm.code" /></label>
        <label>Libellé<input data-testid="plan-label" v-model="planForm.label" /></label>
        <label>Sens
          <select data-testid="plan-direction" v-model="planForm.direction">
            <option value="INBOUND">INBOUND</option><option value="OUTBOUND">OUTBOUND</option>
          </select>
        </label>
        <label>Millésime<input data-testid="plan-vintage" type="number" v-model.number="planForm.vintage" /></label>
        <button class="btn" data-testid="plan-create" @click="createPlan">Créer le plan</button>
      </div>
    </section>

    <section v-if="selectedPlan" class="card" style="overflow-x: auto" data-testid="rules-section">
      <h2 style="margin: 12px 16px">Règles du plan {{ selectedPlan }}</h2>
      <table>
        <thead><tr><th>Code</th><th>Libellé</th><th>Famille</th><th></th></tr></thead>
        <tbody>
          <tr v-if="rules.length === 0"><td colspan="4" data-testid="rules-empty">Aucune règle.</td></tr>
          <tr v-for="r in rules" :key="r.code" :data-testid="`rule-row-${r.code}`">
            <td style="font-family: 'IBM Plex Mono', monospace">{{ r.code }}</td>
            <td>{{ r.label }}</td>
            <td>{{ familyLabel(r.family) }}</td>
            <td><button class="btn" :data-testid="`rule-open-${r.code}`" @click="openRule(r.code)">Versions</button></td>
          </tr>
        </tbody>
      </table>
      <div v-if="canEdit()" style="padding: 12px 16px; display: flex; gap: 8px; flex-wrap: wrap; align-items: end">
        <label>Code<input data-testid="rule-code" v-model="ruleForm.code" /></label>
        <label>Libellé<input data-testid="rule-label" v-model="ruleForm.label" /></label>
        <label>Famille
          <select data-testid="rule-family" v-model="ruleForm.family">
            <option v-for="f in families" :key="f.code" :value="f.code">{{ f.label }}</option>
          </select>
        </label>
        <button class="btn" data-testid="rule-create" @click="createRule">Créer la règle</button>
      </div>
      <div v-if="canEdit()" style="padding: 0 16px 12px; display: flex; gap: 8px; align-items: end">
        <label>Nouveau code<input data-testid="dup-code" v-model="dupForm.newCode" /></label>
        <label>Nouveau millésime<input data-testid="dup-vintage" type="number" v-model.number="dupForm.newVintage" /></label>
        <button class="btn" data-testid="plan-duplicate" @click="duplicatePlan">Dupliquer le plan</button>
      </div>
    </section>

    <section v-if="selectedRule" class="card" style="overflow-x: auto" data-testid="versions-section">
      <h2 style="margin: 12px 16px">Versions et conflits — {{ selectedRule }}</h2>
      <table>
        <thead><tr><th>Version</th><th>Validité</th><th>Statut</th><th>Priorité</th><th>Groupe</th><th>Conflits</th><th></th></tr></thead>
        <tbody>
          <tr v-if="versions.length === 0"><td colspan="7" data-testid="versions-empty">Aucune version.</td></tr>
          <tr v-for="v in versions" :key="v.versionNo" :data-testid="`version-row-${v.versionNo}`">
            <td>{{ v.versionNo }}</td>
            <td>{{ v.validFrom ?? '—' }} → {{ v.validTo ?? '—' }}</td>
            <td><span :class="versionStatusClass(v.status)" data-testid="version-status">{{ v.status ?? '—' }}</span></td>
            <td>{{ v.priority ?? '—' }}</td>
            <td>{{ v.exclusionGroup ?? '—' }}</td>
            <td data-testid="version-conflicts">{{ conflictLabel(v.conflicts) }}</td>
            <td>
              <button v-if="canEdit() && v.status === 'DRAFT'" class="btn" :data-testid="`version-approve-${v.versionNo}`"
                @click="approve(v.versionNo)">Approuver</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="canEdit()" style="padding: 12px 16px; display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 8px">
        <label>Valable du<input data-testid="v-from" type="date" v-model="versionForm.validFrom" /></label>
        <label>Au<input data-testid="v-to" type="date" v-model="versionForm.validTo" /></label>
        <label>Périmètre (JSON)<input data-testid="v-scope" v-model="versionForm.scope" /></label>
        <label>Paramètres (JSON)<input data-testid="v-parameters" v-model="versionForm.parameters" /></label>
        <label>Priorité<input data-testid="v-priority" type="number" v-model.number="versionForm.priority" /></label>
        <label>Groupe d'exclusion<input data-testid="v-group" v-model="versionForm.exclusionGroup" /></label>
        <button class="btn" data-testid="version-create" @click="addVersion">Ajouter la version</button>
      </div>
    </section>
  </div>
</template>
