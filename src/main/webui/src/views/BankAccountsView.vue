<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';

interface BankAccountView {
  entityCode: string;
  holderType: string | null;
  holderName: string;
  iban: string;
  bic: string | null;
  validFrom: string | null;
  validTo: string | null;
}

const rows = ref<BankAccountView[]>([]);
const error = ref('');

async function load() {
  error.value = '';
  try {
    rows.value = await apiGet<BankAccountView[]>('/api/ui/v1/bank-accounts');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Coordonnées bancaires</h1>
      <div class="page__subtitle">IBAN (masqués) reçus des outils de gestion ; un changement est signalé avant le paiement suivant (REF-05).</div>
    </div>
    <div v-if="error" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">{{ error }}</div>
    <section v-else class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Entité</th><th>Titulaire</th><th>Type</th><th>IBAN</th><th>BIC</th><th>Depuis</th></tr></thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="6" data-testid="empty">Aucune coordonnée bancaire.</td></tr>
          <tr v-for="(b, i) in rows" :key="i" data-testid="bank-row">
            <td>{{ b.entityCode }}</td>
            <td>{{ b.holderName }}</td>
            <td>{{ b.holderType ?? '—' }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace" data-testid="bank-iban">{{ b.iban }}</td>
            <td>{{ b.bic ?? '—' }}</td>
            <td>{{ b.validFrom ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
