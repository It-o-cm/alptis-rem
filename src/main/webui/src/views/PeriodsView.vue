<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { periodStateLabel, nextState, canDrivePeriod } from '../presentation/cycle';

interface PeriodSummary {
  entityCode: string;
  entityName: string;
  month: string;
  state: string;
}

interface TransitionView {
  fromState: string;
  toState: string;
  userId: string;
  reason: string | null;
}

interface PeriodDetail {
  period: PeriodSummary;
  transitions: TransitionView[];
  lines: number;
  openAnomalies: number;
  blocking: number;
}

const session = useSessionStore();
const periods = ref<PeriodSummary[]>([]);
const detail = ref<PeriodDetail | null>(null);
const error = ref('');
const message = ref('');

const canDrive = () => canDrivePeriod(session.roles);

async function load() {
  error.value = '';
  try {
    periods.value = await apiGet<PeriodSummary[]>('/api/ui/v1/periods');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

async function open(p: PeriodSummary) {
  detail.value = await apiGet<PeriodDetail>(`/api/ui/v1/periods/${p.entityCode}/${p.month}`);
}

function target(): string | null {
  return detail.value ? nextState(detail.value.period.state) : null;
}

async function advance() {
  if (!detail.value) return;
  const to = target();
  if (!to) return;
  message.value = '';
  error.value = '';
  try {
    const p = detail.value.period;
    await apiPost(`/api/ui/v1/periods/${p.entityCode}/${p.month}/transition`, { toState: to });
    message.value = `Période avancée vers « ${periodStateLabel(to)} ».`;
    await load();
    await open({ ...p });
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Transition refusée';
  }
}

function label(state: string): string {
  return periodStateLabel(state);
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">Avancement des périodes</h1>
      <div class="page__subtitle">
        Les périodes ouvertes de vos entités et leur état dans le cycle mensuel (section 7).
      </div>
    </div>

    <div v-if="error" class="tag ko" data-testid="periods-error">{{ error }}</div>
    <div v-if="message" class="tag ok" data-testid="periods-message">{{ message }}</div>

    <section class="card" style="overflow-x: auto">
      <table>
        <thead>
          <tr><th>Entité</th><th>Mois</th><th>État</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="periods.length === 0"><td colspan="4" data-testid="no-period">Aucune période ouverte.</td></tr>
          <tr v-for="p in periods" :key="p.entityCode + p.month" :data-testid="`period-row-${p.entityCode}-${p.month}`">
            <td>{{ p.entityName }}</td>
            <td>{{ p.month }}</td>
            <td><span class="tag mute">{{ label(p.state) }}</span></td>
            <td><button class="btn" :data-testid="`open-${p.entityCode}-${p.month}`" @click="open(p)">Ouvrir</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="detail" class="card" style="padding: 18px 20px" data-testid="period-detail">
      <h2 style="margin: 0 0 12px; font-size: 16px">
        {{ detail.period.entityName }} — {{ detail.period.month }}
        <span class="tag mute" data-testid="detail-state">{{ label(detail.period.state) }}</span>
      </h2>
      <div class="page__subtitle" data-testid="detail-counts">
        {{ detail.lines }} ligne(s), {{ detail.openAnomalies }} anomalie(s) ouverte(s),
        {{ detail.blocking }} bloquante(s).
      </div>
      <button v-if="canDrive() && target()" class="btn" data-testid="advance-button" @click="advance">
        Avancer vers « {{ label(target() as string) }} »
      </button>
      <table style="margin-top: 14px">
        <thead>
          <tr><th>De</th><th>Vers</th><th>Par</th><th>Motif</th></tr>
        </thead>
        <tbody>
          <tr v-if="detail.transitions.length === 0"><td colspan="4" data-testid="no-transition">Aucune transition.</td></tr>
          <tr v-for="(t, i) in detail.transitions" :key="i" data-testid="transition-row">
            <td>{{ label(t.fromState) }}</td>
            <td>{{ label(t.toState) }}</td>
            <td>{{ t.userId }}</td>
            <td>{{ t.reason ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
