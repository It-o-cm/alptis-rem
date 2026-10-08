<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';

interface ProductView {
  entityCode: string;
  code: string;
  label: string;
  rangeCode: string | null;
  insurerCode: string | null;
  commissionable: boolean;
}

const rows = ref<ProductView[]>([]);
const error = ref('');

async function load() {
  error.value = '';
  try {
    rows.value = await apiGet<ProductView[]>('/api/ui/v1/products');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Produits</h1>
      <div class="page__subtitle">Gammes, produits et options par entité (REF-01).</div>
    </div>
    <div v-if="error" class="card" style="padding: 16px 18px; color: #8f1d16" data-testid="load-error">{{ error }}</div>
    <section v-else class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Entité</th><th>Code</th><th>Libellé</th><th>Gamme</th><th>Assureur</th><th>Commissionnable</th></tr></thead>
        <tbody>
          <tr v-if="rows.length === 0"><td colspan="6" data-testid="empty">Aucun produit.</td></tr>
          <tr v-for="p in rows" :key="p.entityCode + p.code" :data-testid="`product-row-${p.code}`">
            <td>{{ p.entityCode }}</td>
            <td style="font-family: 'IBM Plex Mono', monospace">{{ p.code }}</td>
            <td>{{ p.label }}</td>
            <td>{{ p.rangeCode ?? '—' }}</td>
            <td>{{ p.insurerCode ?? '—' }}</td>
            <td><span :class="p.commissionable ? 'tag ok' : 'tag mute'">{{ p.commissionable ? 'Oui' : 'Non' }}</span></td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
