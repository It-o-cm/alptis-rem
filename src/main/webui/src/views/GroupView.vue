<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { formatAmount } from '../presentation/format';

interface EntitySummary {
  code: string;
  name: string;
  total: number;
}

const rows = ref<EntitySummary[]>([]);
const error = ref('');
const forbidden = ref(false);

function amount(value: number): string {
  return formatAmount(value);
}

function grandTotal(): number {
  return rows.value.reduce((sum, r) => sum + r.total, 0);
}

async function load() {
  error.value = '';
  forbidden.value = false;
  try {
    rows.value = await apiGet<EntitySummary[]>('/api/ui/v1/reporting/consolidated');
  } catch (e) {
    rows.value = [];
    if (e instanceof ApiError && e.status === 404) {
      forbidden.value = true;
    } else {
      error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
    }
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Vision consolidée du groupe</h1>
      <div class="page__subtitle">
        Le total des commissions de chaque entité du groupe, réservé aux utilisateurs habilités (REP-07).
      </div>
    </div>

    <div v-if="forbidden" class="card" data-testid="group-forbidden" style="padding: 28px 20px; font-size: 14px; color: #4a5868">
      La vision consolidée est réservée aux utilisateurs rattachés au groupe.
    </div>
    <div v-if="error" class="tag ko" data-testid="group-error">{{ error }}</div>

    <section v-if="rows.length > 0" class="card" style="overflow-x: auto">
      <table>
        <thead><tr><th>Entité</th><th>Commissions</th></tr></thead>
        <tbody>
          <tr v-for="r in rows" :key="r.code" :data-testid="`group-row-${r.code}`">
            <td>{{ r.name }}</td><td>{{ amount(r.total) }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr><td>Total groupe</td><td data-testid="group-total">{{ amount(grandTotal()) }}</td></tr>
        </tfoot>
      </table>
    </section>
  </div>
</template>
