<script setup lang="ts">
import { ref } from 'vue';
import { apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { formatAmount } from '../presentation/format';

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
const result = ref<Result | null>(null);
const error = ref('');

const canRun = () => canEditEntityParameters(session.roles) || session.roles.includes('OPERATOR');

function amount(value: number): string {
  return formatAmount(value);
}

function addSubstitution() {
  substitutions.value.push({ oldRate: '', newRate: '' });
}

async function run() {
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
  } catch (e) {
    result.value = null;
    error.value = e instanceof ApiError ? e.message : 'Simulation impossible';
  }
}
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Simulation d'une version de règle</h1>
      <div class="page__subtitle">
        Rejoue une période passée avec une version en projet et compare au réel. Aucun effet sur les comptes,
        les bordereaux ni la comptabilité (PAR-13).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; gap: 12px; align-items: end; flex-wrap: wrap">
        <label>Entité <input data-testid="sim-entity" type="text" v-model="entity" /></label>
        <label>Période rejouée <input data-testid="sim-month" type="text" v-model="month" /></label>
        <label>Règle <input data-testid="sim-rule" type="text" v-model="ruleCode" /></label>
        <label>Version <input data-testid="sim-version" type="number" v-model.number="versionNo" /></label>
        <label>Distributeur (optionnel) <input data-testid="sim-scope" type="text" v-model="scopeDistributor" /></label>
      </div>
      <div style="margin-top: 12px">
        <div style="font-size: 13px; font-weight: 600; color: #3d4a59; margin-bottom: 6px">Table de substitution (ancien taux → nouveau taux ; vide = retiré)</div>
        <div v-for="(s, i) in substitutions" :key="i" style="display: flex; gap: 10px; align-items: center; margin-bottom: 6px">
          <input :data-testid="`sim-old-${i}`" aria-label="Ancien taux" type="text" v-model="s.oldRate" placeholder="0.30" style="width: 120px" />
          <span>→</span>
          <input :data-testid="`sim-new-${i}`" aria-label="Nouveau taux" type="text" v-model="s.newRate" placeholder="0.25" style="width: 120px" />
        </div>
        <button class="btn sec" data-testid="sim-add-sub" @click="addSubstitution">Ajouter une substitution</button>
      </div>
      <div style="margin-top: 12px">
        <button v-if="canRun()" class="btn" data-testid="sim-run" @click="run">Lancer la simulation</button>
      </div>
      <div v-if="error" class="tag ko" data-testid="sim-error" style="margin-top: 10px">{{ error }}</div>
    </section>

    <div v-if="!result" class="card" data-testid="sim-idle" style="padding: 28px 20px; font-size: 14px; color: #4a5868">
      Aucune simulation lancée. Choisir une règle, une version et une période, puis lancer.
    </div>

    <template v-if="result">
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px">
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Réel</div>
          <div data-testid="sim-real" style="font: 500 22px monospace; margin-top: 6px">{{ amount(result.realTotal) }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Simulé</div>
          <div data-testid="sim-sim" style="font: 500 22px monospace; margin-top: 6px">{{ amount(result.simulatedTotal) }}</div></div>
        <div class="card" style="padding: 16px 18px; border-color: #0e5a8a"><div style="font-size: 13px; color: #4a5868">Écart</div>
          <div data-testid="sim-ecart" style="font: 500 22px monospace; margin-top: 6px">{{ amount(result.ecart) }}</div></div>
        <div class="card" style="padding: 16px 18px"><div style="font-size: 13px; color: #4a5868">Lignes modifiées</div>
          <div data-testid="sim-modified" style="font: 500 22px monospace; margin-top: 6px">{{ result.linesModified }}</div>
          <div style="font-size: 13px; color: #4a5868; margin-top: 4px">sur {{ result.linesReplayed }} rejouées</div>
          <div style="font-size: 13px; color: #4a5868; margin-top: 4px">Événements en attente : {{ result.held }}</div></div>
      </div>

      <section class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Écart par nature</h2>
        <table>
          <thead><tr><th>Nature</th><th>Réel</th><th>Simulé</th><th>Écart</th></tr></thead>
          <tbody>
            <tr v-for="(n, i) in result.byNature" :key="i" :data-testid="`sim-nature-${n.nature}`">
              <td>{{ n.nature }}</td><td>{{ amount(n.real) }}</td><td>{{ amount(n.simulated) }}</td>
              <td :data-testid="`sim-nature-ecart-${n.nature}`">{{ amount(n.ecart) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="result.topDistributors.length > 0" class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Distributeurs les plus touchés</h2>
        <table>
          <thead><tr><th>Distributeur</th><th>Réel</th><th>Simulé</th><th>Écart</th></tr></thead>
          <tbody>
            <tr v-for="(d, i) in result.topDistributors" :key="i" :data-testid="`sim-dist-${i}`">
              <td>{{ d.distributor }}</td><td>{{ amount(d.real) }}</td><td>{{ amount(d.simulated) }}</td><td>{{ amount(d.ecart) }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
