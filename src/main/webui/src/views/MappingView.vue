<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditMapping } from '../presentation/access';
import { AppButton, AppCard, AppForm, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

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

/** Columns of the mapping test-result table. */
const columns: Column<Record<string, string>>[] = [
  { key: 'source_key', label: 'Clé source' },
  { key: 'event_type', label: "Type d'événement" },
  { key: 'precomputed_amount', label: 'Montant reçu', align: 'num', value: (r) => r.precomputed_amount ?? '—' },
];

/** Loads the list of existing mapping versions. */
async function loadList(): Promise<void> {
  try {
    mappings.value = await apiGet<MappingView[]>('/api/ui/v1/mappings');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  }
}

/**
 * Loads an existing mapping into the editor.
 * @param mapping the selected mapping version
 */
function loadMapping(mapping: MappingView): void {
  flowCode.value = mapping.flowCode;
  sourceSystem.value = mapping.sourceSystem;
  definition.value = mapping.definition;
  result.value = null;
}

/** Runs the current definition against the sample file. */
async function runTest(): Promise<void> {
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

/** Saves the current definition as a new mapping version. */
async function save(): Promise<void> {
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
    <PageHeading
      title="Mapping de fichier"
      subtitle="Décrit comment chaque colonne du fichier d'un partenaire devient un champ du format pivot. Intégrer un nouveau fichier ne demande aucun développement (flux IN-06)."
    />

    <AppCard>
      <AppForm>
        <FormField label="Objet pivot (flux)" width="short" v-slot="{ id }">
          <select :id="id" data-testid="mapping-flow" v-model="flowCode">
            <option value="events">Événements</option>
            <option value="coverages">Garanties</option>
          </select>
        </FormField>
        <FormField label="Système source" width="short" v-slot="{ id }">
          <input :id="id" data-testid="mapping-source" type="text" v-model="sourceSystem" />
        </FormField>
        <FormField v-if="mappings.length > 0" label="Versions existantes" width="long" v-slot="{ id }">
          <select
            :id="id"
            data-testid="mapping-versions"
            @change="(e) => { const m = mappings[(e.target as HTMLSelectElement).selectedIndex - 1]; if (m) loadMapping(m); }"
          >
            <option>—</option>
            <option v-for="m in mappings" :key="m.id">{{ m.flowCode }} · {{ m.sourceSystem }} · v{{ m.versionNo }}</option>
          </select>
        </FormField>
      </AppForm>
    </AppCard>

    <AppCard title="Définition du mapping">
      <textarea
        class="code-area"
        data-testid="mapping-definition"
        aria-label="Définition du mapping"
        v-model="definition"
        rows="12"
      ></textarea>
    </AppCard>

    <AppCard title="Fichier d'exemple">
      <textarea
        class="code-area"
        data-testid="mapping-sample"
        aria-label="Fichier d'exemple"
        v-model="sample"
        rows="4"
      ></textarea>
      <div class="mapping-actions">
        <AppButton variant="secondary" data-testid="mapping-test" @click="runTest">
          Tester sur le fichier d'exemple
        </AppButton>
        <AppButton v-if="canEdit()" data-testid="mapping-save" @click="save">Enregistrer</AppButton>
        <span v-if="message" class="tag ok" data-testid="mapping-message">{{ message }}</span>
        <span v-if="error" class="tag ko" data-testid="mapping-error">{{ error }}</span>
      </div>
    </AppCard>

    <AppCard v-if="result" title="Résultat du test" data-testid="mapping-result">
      <p class="mapping-counts">
        <span data-testid="result-kept">{{ result.kept }} ligne(s) conforme(s)</span> ·
        <span data-testid="result-filtered">{{ result.filtered }} ligne(s) filtrée(s)</span>
      </p>
      <DataTable :columns="columns" :rows="result.rows" :row-testid="() => 'result-row'">
        <template #cell-source_key="{ row }"><span class="mono">{{ row.source_key }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>

<style scoped>
.code-area {
  width: 100%;
  font-family: 'IBM Plex Mono', monospace;
  font-size: var(--fs-small);
}
.mapping-actions {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  flex-wrap: wrap;
}
.mapping-counts {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--muted);
}
</style>
