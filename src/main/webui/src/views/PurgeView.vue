<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';

const eligible = ref<number | null>(null);
const message = ref('');
const error = ref('');

async function load() {
  error.value = '';
  try {
    eligible.value = (await apiGet<{ count: number }>('/api/ui/v1/admin/purge/eligible')).count;
  } catch (e) {
    eligible.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function purge() {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ count: number }>('/api/ui/v1/admin/purge', {});
    message.value = `${result.count} enregistrement(s) anonymisé(s).`;
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Purge impossible';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Purge et anonymisation</h1>
      <div class="page__subtitle">
        Anonymise les données personnelles à l'échéance de la durée de conservation (dix ans) : nom et date
        de naissance effacés, lignes et écritures conservées (ADM-06).
      </div>
    </div>

    <section class="card" style="padding: 18px 20px">
      <div style="font-size: 14px">
        Bénéficiaires éligibles à l'anonymisation :
        <strong data-testid="purge-eligible">{{ eligible === null ? '—' : eligible }}</strong>
      </div>
      <button class="btn" data-testid="purge-run" style="margin-top: 12px" @click="purge">Anonymiser</button>
      <div v-if="error" class="tag ko" data-testid="purge-error" style="margin-top: 10px">{{ error }}</div>
      <div v-if="message" class="tag ok" data-testid="purge-message" style="margin-top: 10px">{{ message }}</div>
    </section>
  </div>
</template>
