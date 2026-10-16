<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { AppButton, AppCard, AppMessage, PageHeading } from '../components/ui';
import { formatAmount } from '../presentation/format';

const eligible = ref<number | null>(null);
const message = ref('');
const error = ref('');

/** Loads the count of beneficiaries eligible for anonymisation. */
async function load() {
  error.value = '';
  try {
    eligible.value = (await apiGet<{ count: number }>('/api/ui/v1/admin/purge/eligible')).count;
  } catch (e) {
    eligible.value = null;
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

/** Runs the anonymisation and reloads the eligible count. */
async function purge() {
  message.value = '';
  error.value = '';
  try {
    const result = await apiPost<{ count: number; preservedAmount: number | null }>(
      '/api/ui/v1/admin/purge',
      {},
    );
    message.value =
      `${result.count} enregistrement(s) anonymisé(s). ` +
      `Montants conservés : ${formatAmount(result.preservedAmount)}.`;
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Purge impossible';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Purge et anonymisation"
      subtitle="Anonymise les données personnelles à l'échéance de la durée de conservation (dix ans) : nom et date de naissance effacés, lignes et écritures conservées (ADM-06)."
    />

    <AppCard>
      <p>
        Bénéficiaires éligibles à l'anonymisation :
        <strong data-testid="purge-eligible">{{ eligible === null ? '—' : eligible }}</strong>
      </p>
      <div>
        <AppButton variant="danger" data-testid="purge-run" @click="purge">Anonymiser</AppButton>
      </div>
      <AppMessage v-if="error" variant="error" data-testid="purge-error">{{ error }}</AppMessage>
      <AppMessage v-if="message" variant="success" data-testid="purge-message">{{ message }}</AppMessage>
    </AppCard>
  </div>
</template>
