<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, EntitySelect, FilterBar, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface TemplateView {
  documentType: string;
  versionNo: number;
  content: string;
  validFrom: string | null;
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 && session.entities[0] !== '*' ? session.entities[0] : 'ALPTIS_ASSURANCES');
const rows = ref<TemplateView[]>([]);
const form = ref({ documentType: 'STATEMENT', content: '' });
const error = ref('');
const message = ref('');
const loading = ref(false);

/** Whether the current user (business administrator) may edit the templates (BRD-05). */
const canEdit = (): boolean => canEditEntityParameters(session.roles);

/** Columns of the templates table. */
const columns: Column<TemplateView>[] = [
  { key: 'documentType', label: 'Type' },
  { key: 'versionNo', label: 'Version', align: 'num' },
  { key: 'validFrom', label: 'Depuis', value: (t) => t.validFrom ?? '—' },
  { key: 'content', label: 'Contenu (logo, mentions, colonnes, ordre)' },
];

/** Loads the document templates of the entity. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    rows.value = await apiGet<TemplateView[]>(`/api/ui/v1/documents/templates/${entity.value}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

/** Saves a new version of the selected template type. */
async function save(): Promise<void> {
  error.value = '';
  message.value = '';
  try {
    const saved = await apiPost<TemplateView>(`/api/ui/v1/documents/templates/${entity.value}`, {
      documentType: form.value.documentType,
      content: form.value.content,
    });
    message.value = `Modèle ${saved.documentType} enregistré en version ${saved.versionNo}.`;
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Enregistrement refusé';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Modèles de documents"
      subtitle="L'administrateur métier modifie les modèles : logo, mentions, colonnes, ordre (BRD-05)."
    />

    <FilterBar :loading="loading" search-label="Rafraîchir" search-testid="tpl-refresh" :resettable="false" @search="load">
      <FormField label="Entité" stacked width="short" v-slot="{ id }">
        <EntitySelect :id="id" data-testid="tpl-entity" v-model="entity" />
      </FormField>
    </FilterBar>

    <AppMessage v-if="error" variant="error" data-testid="tpl-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="tpl-message">{{ message }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucun modèle."
        empty-testid="tpl-empty"
        :row-testid="(t) => `tpl-row-${t.documentType}`"
      />
    </AppCard>

    <AppCard v-if="canEdit()" title="Modifier un modèle">
      <AppForm @submit="save">
        <FormField label="Type" width="short" v-slot="{ id }">
          <select :id="id" data-testid="tpl-type" v-model="form.documentType">
            <option value="STATEMENT">Bordereau</option>
            <option value="CORRECTED_STATEMENT">Bordereau rectificatif</option>
            <option value="MEMBER_STATEMENT">Relevé du membre</option>
            <option value="TAX_CERTIFICATE">Attestation fiscale</option>
            <option value="ADVANCE_SUMMARY">Synthèse d'escompte</option>
          </select>
        </FormField>
        <FormField label="Contenu" width="long" v-slot="{ id }">
          <textarea :id="id" data-testid="tpl-content" rows="4" v-model="form.content"></textarea>
        </FormField>
        <AppButton type="submit" data-testid="tpl-save">Enregistrer</AppButton>
      </AppForm>
    </AppCard>
  </div>
</template>
