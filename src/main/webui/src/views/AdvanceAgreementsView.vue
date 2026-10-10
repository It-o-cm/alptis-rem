<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface AdvanceView {
  entityCode: string; partnerCode: string | null; validFrom: string | null; validTo: string | null;
  anticipationDays: number | null; maxContracts: number | null; status: string | null;
}

const session = useSessionStore();
const entityCode = (): string => (session.entities.length > 0 ? session.entities[0] : '');
const canEdit = (): boolean => canEditEntityParameters(session.roles);

const rows = ref<AdvanceView[]>([]);
const error = ref('');
const message = ref('');
const loading = ref(false);
const form = ref({ partnerCode: '', validFrom: '', validTo: '', anticipationDays: 360, maxContracts: 50, unpaidRankThreshold: 2 });

/** Columns of the advance agreements table. */
const columns: Column<AdvanceView>[] = [
  { key: 'partnerCode', label: 'Distributeur', value: (a) => a.partnerCode ?? '—' },
  { key: 'validFrom', label: 'Du', value: (a) => a.validFrom ?? '—' },
  { key: 'validTo', label: 'Au', value: (a) => a.validTo ?? '—' },
  { key: 'anticipationDays', label: 'Anticipation (j)', align: 'num', value: (a) => a.anticipationDays ?? '—' },
  { key: 'maxContracts', label: 'Plafond', align: 'num', value: (a) => a.maxContracts ?? '—' },
  { key: 'status', label: 'Statut' },
];

/** Loads the advance agreements of the user's entities. */
async function load(): Promise<void> {
  loading.value = true;
  try {
    rows.value = await apiGet<AdvanceView[]>('/api/ui/v1/advance-agreements');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  } finally {
    loading.value = false;
  }
}

/** Creates an advance agreement from the form and reloads the list. */
async function create(): Promise<void> {
  error.value = '';
  message.value = '';
  try {
    await apiPost('/api/ui/v1/advance-agreements', {
      entityCode: entityCode(), partnerCode: form.value.partnerCode,
      validFrom: form.value.validFrom || null, validTo: form.value.validTo || null,
      anticipationDays: form.value.anticipationDays, maxContracts: form.value.maxContracts,
      unpaidRankThreshold: form.value.unpaidRankThreshold,
    });
    message.value = "Convention d'escompte enregistrée.";
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Conventions d'escompte"
      subtitle="Anticipation, plafond et rang d'impayé par distributeur (PAR-09)."
    />

    <AppMessage v-if="error" variant="error" data-testid="advance-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="advance-message">{{ message }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucune convention."
        empty-testid="advance-empty"
        :row-testid="(a) => `advance-row-${rows.indexOf(a)}`"
      >
        <template #cell-status="{ row }"><span class="tag ok">{{ row.status ?? '—' }}</span></template>
      </DataTable>
    </AppCard>

    <AppCard v-if="canEdit()">
      <AppForm @submit="create">
        <FormField label="Distributeur" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adv-partner" v-model="form.partnerCode" />
        </FormField>
        <FormField label="Du" width="date" v-slot="{ id }">
          <input :id="id" data-testid="adv-from" type="date" v-model="form.validFrom" />
        </FormField>
        <FormField label="Au" width="date" v-slot="{ id }">
          <input :id="id" data-testid="adv-to" type="date" v-model="form.validTo" />
        </FormField>
        <FormField label="Anticipation" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adv-days" type="number" v-model.number="form.anticipationDays" />
        </FormField>
        <FormField label="Plafond" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adv-cap" type="number" v-model.number="form.maxContracts" />
        </FormField>
        <AppButton type="submit" data-testid="adv-create">Enregistrer</AppButton>
      </AppForm>
    </AppCard>
  </div>
</template>
