<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { schemaFields, type SchemaField } from '../presentation/rules';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface FamilyView { code: string; label: string; }

const families = ref<FamilyView[]>([]);
const selected = ref<string | null>(null);
const fields = ref<SchemaField[]>([]);
const error = ref('');

/** Columns of the calculation-families table. */
const columns: Column<FamilyView>[] = [
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Famille' },
];

/** Loads the list of calculation families. */
async function load(): Promise<void> {
  try {
    families.value = await apiGet<FamilyView[]>('/api/ui/v1/families');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

/**
 * Loads a family's parameter schema and derives its form fields.
 * @param code the family code to open
 */
async function openFamily(code: string): Promise<void> {
  selected.value = code;
  try {
    const schema = await apiGet<unknown>(`/api/ui/v1/families/${code}/schema`);
    fields.value = schemaFields(JSON.stringify(schema));
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

/**
 * Help text describing a schema field's type and whether it is required.
 * @param field the schema field
 */
function fieldHelp(field: SchemaField): string {
  return `(${field.type}${field.required ? ', requis' : ''})`;
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Familles de calcul"
      subtitle="Un formulaire par famille, dérivé de son schéma de paramètres (PAR-02)."
    />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable :columns="columns" :rows="families" row-key="code" :row-testid="(f) => `family-row-${f.code}`">
        <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`family-open-${row.code}`" @click="openFamily(row.code)">
            Formulaire
          </AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="selected" data-testid="family-form" :title="`Paramètres de ${selected}`">
      <div v-if="fields.length === 0" data-testid="family-nofields">Aucun paramètre dédié.</div>
      <AppForm v-else>
        <FormField
          v-for="field in fields"
          :key="field.name"
          :data-testid="`field-${field.name}`"
          :label="field.name"
          :help="fieldHelp(field)"
          width="short"
          v-slot="{ id, describedby }"
        >
          <input :id="id" :aria-describedby="describedby" :placeholder="field.type" />
        </FormField>
      </AppForm>
    </AppCard>
  </div>
</template>
