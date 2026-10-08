<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { schemaFields, type SchemaField } from '../presentation/rules';

interface FamilyView { code: string; label: string; }

const families = ref<FamilyView[]>([]);
const selected = ref<string | null>(null);
const fields = ref<SchemaField[]>([]);
const error = ref('');

async function load() {
  try {
    families.value = await apiGet<FamilyView[]>('/api/ui/v1/families');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

async function openFamily(code: string) {
  selected.value = code;
  try {
    const schema = await apiGet<unknown>(`/api/ui/v1/families/${code}/schema`);
    fields.value = schemaFields(JSON.stringify(schema));
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Familles de calcul</h1>
      <div class="page__subtitle">Un formulaire par famille, dérivé de son schéma de paramètres (PAR-02).</div>
    </div>
    <div v-if="error" class="card" style="padding: 12px 16px; color: #8f1d16" data-testid="load-error">{{ error }}</div>
    <section class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Code</th><th>Famille</th><th></th></tr></thead>
        <tbody>
          <tr v-for="f in families" :key="f.code" :data-testid="`family-row-${f.code}`">
            <td style="font-family: 'IBM Plex Mono', monospace">{{ f.code }}</td>
            <td>{{ f.label }}</td>
            <td><button class="btn" :data-testid="`family-open-${f.code}`" @click="openFamily(f.code)">Formulaire</button></td>
          </tr>
        </tbody>
      </table>
    </section>
    <section v-if="selected" class="card" style="padding: 12px 16px" data-testid="family-form">
      <h2>Paramètres de {{ selected }}</h2>
      <div v-if="fields.length === 0" data-testid="family-nofields">Aucun paramètre dédié.</div>
      <div v-for="field in fields" :key="field.name" :data-testid="`field-${field.name}`" style="margin: 6px 0">
        <label>{{ field.name }} <small>({{ field.type }}{{ field.required ? ', requis' : '' }})</small>
          <input :placeholder="field.type" />
        </label>
      </div>
    </section>
  </div>
</template>
