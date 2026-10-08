<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditMapping } from '../presentation/access';

interface MappingView {
  id: number;
  entityCode: string;
  flowCode: string;
  sourceSystem: string;
  versionNo: number;
  definition: string;
}

interface TestResult {
  rows: Array<Record<string, string>>;
  kept: number;
  filtered: number;
}

const DEFAULT_DEFINITION = JSON.stringify(
  {
    filter: { column: 'NOM_MUTUELLE', equals: 'ALPTIS' },
    fields: {
      source_key: { column: 'CODE_MOUVEMENT' },
      event_type: { column: 'TYPE_MOUVEMENT', transcode: { 'Versement programmé': 'SCHEDULED_PAYMENT' } },
      contract_number: { column: 'NUMERO_CONTRAT' },
      event_date: { column: 'DATE_MOUVEMENT' },
    },
    derived: [
      {
        when: { column: 'MONTANT_COMMISSION' },
        fields: {
          source_key: { column: 'CODE_MOUVEMENT', suffix: '-COM' },
          event_type: { const: 'INSURER_COMMISSION' },
          precomputed_amount: { column: 'MONTANT_COMMISSION' },
        },
      },
    ],
  },
  null,
  2,
);

const DEFAULT_SAMPLE =
  'CODE_MOUVEMENT;TYPE_MOUVEMENT;NUMERO_CONTRAT;DATE_MOUVEMENT;NOM_MUTUELLE;MONTANT_COMMISSION\n' +
  'MVT-1;Versement programmé;PER-1;2026-09-15;ALPTIS;30.00\n' +
  'MVT-1;Versement programmé;PER-1;2026-09-15;UMR;0.19\n';

const session = useSessionStore();
const mappings = ref<MappingView[]>([]);
const flowCode = ref('events');
const sourceSystem = ref('UMR');
const definition = ref(DEFAULT_DEFINITION);
const sample = ref(DEFAULT_SAMPLE);
const result = ref<TestResult | null>(null);
const message = ref('');
const error = ref('');

const canEdit = () => canEditMapping(session.roles);
const entityCode = () => (session.entities.length > 0 ? session.entities[0] : '');

async function loadList() {
  try {
    mappings.value = await apiGet<MappingView[]>('/api/ui/v1/mappings');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  }
}

function loadMapping(mapping: MappingView) {
  flowCode.value = mapping.flowCode;
  sourceSystem.value = mapping.sourceSystem;
  definition.value = mapping.definition;
  result.value = null;
}

async function runTest() {
  message.value = '';
  error.value = '';
  result.value = null;
  try {
    result.value = await apiPost<TestResult>('/api/ui/v1/mappings/test', {
      definition: definition.value,
      sample: sample.value,
    });
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Échec du test';
  }
}

async function save() {
  message.value = '';
  error.value = '';
  try {
    const created = await apiPost<MappingView>('/api/ui/v1/mappings', {
      entityCode: entityCode(),
      flowCode: flowCode.value,
      sourceSystem: sourceSystem.value,
      definition: definition.value,
    });
    message.value = `Version ${created.versionNo} enregistrée.`;
    await loadList();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : "Échec de l'enregistrement";
  }
}

onMounted(loadList);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Mapping de fichier</h1>
      <div class="page__subtitle">
        Décrit comment chaque colonne du fichier d'un partenaire devient un champ du format pivot.
        Intégrer un nouveau fichier ne demande aucun développement (flux IN-06).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="display: flex; flex-wrap: wrap; gap: 12px; align-items: end">
        <label>Objet pivot (flux)
          <select data-testid="mapping-flow" v-model="flowCode">
            <option value="events">Événements</option>
            <option value="coverages">Garanties</option>
          </select>
        </label>
        <label>Système source
          <input data-testid="mapping-source" type="text" v-model="sourceSystem" />
        </label>
        <label v-if="mappings.length > 0">Versions existantes
          <select data-testid="mapping-versions" @change="(e) => { const m = mappings[(e.target as HTMLSelectElement).selectedIndex - 1]; if (m) loadMapping(m); }">
            <option>—</option>
            <option v-for="m in mappings" :key="m.id">{{ m.flowCode }} · {{ m.sourceSystem }} · v{{ m.versionNo }}</option>
          </select>
        </label>
      </div>
    </section>

    <section class="card" style="padding: 18px 20px">
      <h2 style="margin: 0 0 8px; font-size: 16px">Définition du mapping</h2>
      <textarea data-testid="mapping-definition" aria-label="Définition du mapping" v-model="definition" rows="12" style="width: 100%; font-family: 'IBM Plex Mono', monospace; font-size: 12px"></textarea>
    </section>

    <section class="card" style="padding: 18px 20px">
      <h2 style="margin: 0 0 8px; font-size: 16px">Fichier d'exemple</h2>
      <textarea data-testid="mapping-sample" aria-label="Fichier d'exemple" v-model="sample" rows="4" style="width: 100%; font-family: 'IBM Plex Mono', monospace; font-size: 12px"></textarea>
      <div style="margin-top: 12px; display: flex; gap: 10px; align-items: center">
        <button class="btn sec" data-testid="mapping-test" @click="runTest">Tester sur le fichier d'exemple</button>
        <button v-if="canEdit()" class="btn" data-testid="mapping-save" @click="save">Enregistrer</button>
        <span v-if="message" class="tag ok" data-testid="mapping-message">{{ message }}</span>
        <span v-if="error" class="tag ko" data-testid="mapping-error">{{ error }}</span>
      </div>
    </section>

    <section v-if="result" class="card" style="overflow-x: auto" data-testid="mapping-result">
      <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Résultat du test</h2>
      <div style="padding: 0 18px 8px; font-size: 14px; color: #4A5868">
        <span data-testid="result-kept">{{ result.kept }} ligne(s) conforme(s)</span> ·
        <span data-testid="result-filtered">{{ result.filtered }} ligne(s) filtrée(s)</span>
      </div>
      <table>
        <thead><tr><th>Clé source</th><th>Type d'événement</th><th class="num">Montant reçu</th></tr></thead>
        <tbody>
          <tr v-for="(r, i) in result.rows" :key="i" data-testid="result-row">
            <td style="font-family: 'IBM Plex Mono', monospace; font-size: 13px">{{ r.source_key }}</td>
            <td>{{ r.event_type }}</td>
            <td class="num">{{ r.precomputed_amount ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
