<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';
import { useSessionStore } from '../stores/session';
import { canMapExternalCode } from '../presentation/access';

interface ExternalCodeView {
  objectType: string;
  internalCode: string | null;
  sourceSystem: string;
  externalCode: string;
}

const session = useSessionStore();
const rows = ref<ExternalCodeView[]>([]);
const error = ref('');
const message = ref('');
const loading = ref(false);
const saving = ref(false);
const form = ref({ sourceSystem: '', externalCode: '', internalContractNumber: '' });

/** Columns of the external-codes list. */
const columns: Column<ExternalCodeView>[] = [
  { key: 'objectType', label: 'Objet' },
  { key: 'internalCode', label: 'Code interne', value: (c) => c.internalCode ?? '—' },
  { key: 'sourceSystem', label: 'Système source' },
  { key: 'externalCode', label: 'Code externe' },
];

/** Whether the current user may add a correspondence. */
const canMap = (): boolean => canMapExternalCode(session.roles);

/** The entity the correspondence is added for (the user's first visible entity). */
const entityCode = (): string => (session.entities.length > 0 ? session.entities[0] : '');

/** Loads the external-code correspondences of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<ExternalCodeView[]>('/api/ui/v1/external-codes');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

/** Maps a source system's contract identifier to an internal contract (REF-04, INT-22). */
async function mapContract(): Promise<void> {
  error.value = '';
  message.value = '';
  saving.value = true;
  try {
    await apiPost('/api/ui/v1/external-codes', {
      entityCode: entityCode(),
      sourceSystem: form.value.sourceSystem,
      externalCode: form.value.externalCode,
      internalContractNumber: form.value.internalContractNumber,
    });
    message.value = `Correspondance ajoutée : ${form.value.sourceSystem}/${form.value.externalCode} → ${form.value.internalContractNumber}.`;
    form.value = { sourceSystem: '', externalCode: '', internalContractNumber: '' };
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Ajout refusé';
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Codes externes"
      subtitle="Correspondance des identifiants internes par système source (REF-04)."
    />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="map-message">{{ message }}</AppMessage>

    <AppCard v-if="canMap()" data-testid="map-form" title="Rattacher un numéro de contrat externe"
             subtitle="Un contrat désigné par le code d'un système source est rattaché à l'adhésion interne ; les événements mis en attente sur ce code sont débloqués au prochain calcul (REF-04).">
      <AppForm @submit="mapContract">
        <FormField label="Système source" width="short" v-slot="{ id }">
          <input :id="id" data-testid="map-source-system" type="text" v-model="form.sourceSystem" />
        </FormField>
        <FormField label="Code externe" width="short" v-slot="{ id }">
          <input :id="id" data-testid="map-external-code" type="text" v-model="form.externalCode" />
        </FormField>
        <FormField label="Contrat interne" width="short" v-slot="{ id }">
          <input :id="id" data-testid="map-internal-contract" type="text" v-model="form.internalContractNumber" />
        </FormField>
        <AppButton type="submit" :loading="saving" data-testid="map-submit">Rattacher</AppButton>
      </AppForm>
    </AppCard>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucune correspondance."
        empty-testid="empty"
        :row-testid="(c) => `code-row-${rows.indexOf(c)}`"
      >
        <template #cell-internalCode="{ row }"><span class="mono">{{ row.internalCode ?? '—' }}</span></template>
        <template #cell-externalCode="{ row }"><span class="mono">{{ row.externalCode }}</span></template>
      </DataTable>
    </AppCard>
  </div>
</template>
