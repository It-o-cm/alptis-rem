<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiGetText, ApiError } from '../api/client';
import { formatAmount } from '../presentation/format';

interface EntitySummary {
  code: string;
  name: string;
  total: number;
}
interface EntityMonth {
  entity: string;
  month: string;
  total: number;
}
interface Consolidated {
  months: string[];
  summaries: EntitySummary[];
  cells: EntityMonth[];
}

const data = ref<Consolidated | null>(null);
const csv = ref('');
const error = ref('');
const forbidden = ref(false);

function amount(value: number): string {
  return formatAmount(value);
}

async function load() {
  error.value = '';
  forbidden.value = false;
  csv.value = '';
  try {
    data.value = await apiGet<Consolidated>('/api/ui/v1/reporting/consolidated');
  } catch (e) {
    data.value = null;
    if (e instanceof ApiError && e.status === 404) {
      forbidden.value = true;
    } else {
      error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
    }
  }
}

async function exportCsv() {
  csv.value = await apiGetText('/api/ui/v1/reporting/export/consolidated');
}

function cell(entity: string, month: string): number | null {
  const found = data.value?.cells.find((c) => c.entity === entity && c.month === month);
  return found ? found.total : null;
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Vision consolidée groupe</h1>
      <div class="page__subtitle">Commissions de chaque entité, mois par mois, pour les utilisateurs groupe (REP-07).</div>
    </div>

    <section v-if="forbidden" class="card" data-testid="group-forbidden" style="padding: 18px 20px">
      La vision consolidée est réservée aux utilisateurs de niveau groupe.
    </section>

    <template v-else>
      <section class="card" style="padding: 14px 20px">
        <button class="btn sec" data-testid="group-export" @click="exportCsv">Exporter CSV</button>
        <div v-if="error" class="tag ko" data-testid="group-error" style="margin-top: 10px">{{ error }}</div>
        <pre v-if="csv" data-testid="group-export-content" style="margin-top: 10px; overflow-x: auto; background: #f6f8fa; padding: 12px">{{ csv }}</pre>
      </section>

      <section v-if="data" class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Total par entité</h2>
        <table>
          <thead><tr><th>Entité</th><th>Commissions</th></tr></thead>
          <tbody>
            <tr v-for="e in data.summaries" :key="e.code" :data-testid="`group-row-${e.code}`">
              <td>{{ e.name }}</td><td class="num">{{ amount(e.total) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="data" class="card" style="overflow-x: auto">
        <h2 style="margin: 0; padding: 16px 18px 6px; font-size: 16px">Entités par mois</h2>
        <table>
          <thead>
            <tr><th>Entité</th><th v-for="m in data.months" :key="m">{{ m }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="e in data.summaries" :key="e.code" :data-testid="`group-month-row-${e.code}`">
              <td>{{ e.code }}</td>
              <td v-for="m in data.months" :key="m" class="num">{{ amount(cell(e.code, m) ?? 0) }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
