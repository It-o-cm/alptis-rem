<script setup lang="ts">
import { ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { AppButton, AppCard, AppForm, AppMessage, EntitySelect, FormField, PageHeading, PlanSelect } from '../components/ui';

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const planCode = ref('');
const newCode = ref('');
const exported = ref<unknown | null>(null);
const json = ref('');
const error = ref('');
const message = ref('');

/** Whether the current user may import a plan. */
const canImport = (): boolean => canEditEntityParameters(session.roles);

/** Exports the selected plan and shows its open-format JSON. */
async function exportPlan(): Promise<void> {
  error.value = '';
  message.value = '';
  try {
    exported.value = await apiGet(`/api/ui/v1/parameters/${entity.value}/plans/${planCode.value}/export`);
    json.value = JSON.stringify(exported.value, null, 2);
  } catch (e) {
    exported.value = null;
    json.value = '';
    error.value = e instanceof ApiError ? e.message : 'Export impossible';
  }
}

/** Imports the exported (or edited) plan under a new code, as draft versions. */
async function importPlan(): Promise<void> {
  error.value = '';
  message.value = '';
  try {
    const body = exported.value ?? JSON.parse(json.value);
    const result = await apiPost<{ planCode: string }>(
      `/api/ui/v1/parameters/${entity.value}/import?newCode=${newCode.value}`,
      body,
    );
    message.value = `Plan importé : ${result.planCode}`;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Import impossible';
  }
}
</script>

<template>
  <div class="page">
    <PageHeading
      title="Export et import du paramétrage"
      subtitle="Exporte un plan complet au format ouvert et le réimporte sous un nouveau code, pour la promotion entre environnements et la réversibilité (PAR-15). Les versions importées sont en projet."
    />

    <AppCard>
      <AppForm @submit="exportPlan">
        <FormField label="Entité" width="short" v-slot="{ id }">
          <EntitySelect :id="id" data-testid="pt-entity" v-model="entity" />
        </FormField>
        <FormField label="Plan à exporter" width="short" v-slot="{ id }">
          <PlanSelect :id="id" data-testid="pt-plan" v-model="planCode" />
        </FormField>
        <AppButton type="submit" data-testid="pt-export">Exporter</AppButton>
      </AppForm>

      <AppForm v-if="canImport()" @submit="importPlan">
        <FormField label="Nouveau code" width="short" v-slot="{ id }">
          <input :id="id" data-testid="pt-newcode" type="text" v-model="newCode" />
        </FormField>
        <AppButton variant="secondary" type="submit" data-testid="pt-import">Importer sous ce code</AppButton>
      </AppForm>

      <AppMessage v-if="error" variant="error" data-testid="pt-error">{{ error }}</AppMessage>
      <AppMessage v-if="message" variant="success" data-testid="pt-message">{{ message }}</AppMessage>
    </AppCard>

    <AppCard v-if="json" data-testid="pt-json-card" title="Paramétrage exporté">
      <textarea
        class="pt-json"
        data-testid="pt-json"
        aria-label="Paramétrage exporté"
        v-model="json"
        rows="16"
      ></textarea>
    </AppCard>
  </div>
</template>

<style scoped>
.pt-json {
  width: 100%;
  box-sizing: border-box;
  font-family: 'IBM Plex Mono', monospace;
  font-size: var(--fs-small);
}
</style>
