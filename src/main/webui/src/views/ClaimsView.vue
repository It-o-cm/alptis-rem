<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canOperateCycle, claimStatusLabel } from '../presentation/cycle';
import { adjustmentStatusLabel, adjustmentTypeLabel, lineNatureLabel, lineStatusLabel } from '../presentation/lines';
import { formatAmount, formatDate } from '../presentation/format';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, DistributorSelect, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface ClaimView {
  id: number;
  distributorCode: string | null;
  distributorName: string | null;
  subject: string;
  status: string;
  receivedAt: string;
  closedAt: string | null;
  resolution: string | null;
  lineCount: number;
}

interface ContestedLine {
  lineId: number;
  nature: string | null;
  amount: number | null;
  status: string | null;
}

interface LinkedAdjustment {
  id: number;
  type: string | null;
  amount: number | null;
  reason: string | null;
  status: string | null;
  correctedLineId: number;
}

interface ClaimDetail {
  claim: ClaimView;
  lines: ContestedLine[];
  linkedAdjustments: LinkedAdjustment[];
}

const session = useSessionStore();
const entity = ref(session.entities.length > 0 ? session.entities[0] : '');
const claims = ref<ClaimView[]>([]);
const detail = ref<ClaimDetail | null>(null);
const error = ref('');
const message = ref('');
const form = ref({ distributorCode: '', subject: '', contestedLineIds: '' });
const resolution = ref('');

const canOperate = (): boolean => canOperateCycle(session.roles);

/** Columns of the claims list. */
const columns: Column<ClaimView>[] = [
  { key: 'distributorName', label: 'Distributeur', value: (c) => c.distributorName ?? c.distributorCode ?? '—' },
  { key: 'subject', label: 'Objet' },
  { key: 'status', label: 'Statut', value: (c) => claimStatusLabel(c.status) },
  { key: 'receivedAt', label: 'Reçue le', value: (c) => formatDate(c.receivedAt) },
  { key: 'lineCount', label: 'Lignes', align: 'num', value: (c) => String(c.lineCount) },
];

/** Columns of the contested lines table. */
const lineColumns: Column<ContestedLine>[] = [
  { key: 'nature', label: 'Nature', value: (l) => lineNatureLabel(l.nature) },
  { key: 'status', label: 'Statut', value: (l) => lineStatusLabel(l.status) },
  { key: 'amount', label: 'Montant', align: 'num', value: (l) => formatAmount(l.amount ?? undefined) },
];

/** Columns of the linked (regularising) adjustments table. */
const adjustmentColumns: Column<LinkedAdjustment>[] = [
  { key: 'type', label: 'Type', value: (a) => adjustmentTypeLabel(a.type) },
  { key: 'status', label: 'Statut', value: (a) => adjustmentStatusLabel(a.status) },
  { key: 'reason', label: 'Motif', value: (a) => a.reason ?? '—' },
  { key: 'amount', label: 'Montant', align: 'num', value: (a) => formatAmount(a.amount ?? undefined) },
];

/** Loads the entity's claims. */
async function load(): Promise<void> {
  error.value = '';
  try {
    claims.value = await apiGet<ClaimView[]>(`/api/ui/v1/claims/${entity.value}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  }
}

/** Opens a claim's detail. */
async function open(c: ClaimView): Promise<void> {
  resolution.value = '';
  detail.value = await apiGet<ClaimDetail>(`/api/ui/v1/claims/detail/${c.id}`);
}

/** Records a new claim from the form. */
async function record(): Promise<void> {
  message.value = '';
  error.value = '';
  try {
    const ids = form.value.contestedLineIds
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s !== '')
      .map((s) => Number(s));
    await apiPost(`/api/ui/v1/claims/${entity.value}`, {
      distributorCode: form.value.distributorCode,
      subject: form.value.subject,
      contestedLineIds: ids,
    });
    message.value = 'Réclamation enregistrée.';
    form.value = { distributorCode: '', subject: '', contestedLineIds: '' };
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Enregistrement refusé';
  }
}

/** Takes the open claim into treatment (OPEN → IN_PROGRESS). */
async function take(): Promise<void> {
  if (!detail.value) return;
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/claims/${detail.value.claim.id}/take`, {});
    message.value = 'Réclamation prise en charge.';
    await open(detail.value.claim);
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Prise en charge refusée';
  }
}

/** Closes the open claim with the entered resolution. */
async function resolve(): Promise<void> {
  if (!detail.value) return;
  message.value = '';
  error.value = '';
  try {
    await apiPost(`/api/ui/v1/claims/${detail.value.claim.id}/resolve`, { resolution: resolution.value });
    message.value = 'Réclamation close.';
    await open(detail.value.claim);
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Clôture refusée';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Réclamations"
      subtitle="Les réclamations des distributeurs, liées aux lignes contestées, suivies jusqu'à la régularisation (REG-06)."
    />

    <AppMessage v-if="error" variant="error" data-testid="claim-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="claim-message">{{ message }}</AppMessage>

    <AppCard v-if="canOperate()" title="Enregistrer une réclamation" data-testid="claim-form">
      <AppForm twoColumn @submit="record">
        <FormField label="Distributeur" width="short" v-slot="{ id }">
          <DistributorSelect :id="id" data-testid="claim-distributor" v-model="form.distributorCode" allow-empty />
        </FormField>
        <FormField label="Objet" width="long" v-slot="{ id }">
          <input :id="id" data-testid="claim-subject" type="text" v-model="form.subject" />
        </FormField>
        <FormField label="Lignes contestées (ids, séparés par des virgules)" width="long" v-slot="{ id }">
          <input :id="id" data-testid="claim-lines" type="text" v-model="form.contestedLineIds" />
        </FormField>
        <AppButton type="submit" data-testid="claim-submit">Enregistrer</AppButton>
      </AppForm>
    </AppCard>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="claims"
        empty="Aucune réclamation."
        empty-testid="no-claim"
        :row-testid="(c) => `claim-row-${c.id}`"
      >
        <template #cell-status="{ row }">
          <span :class="row.status === 'CLOSED' ? 'tag ok' : 'tag warn'">{{ claimStatusLabel(row.status) }}</span>
        </template>
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`open-claim-${row.id}`" @click="open(row)">Fiche</AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="claim-detail" :title="`Réclamation — ${detail.claim.subject}`">
      <p class="page__subtitle" data-testid="claim-detail-status">
        Statut : {{ claimStatusLabel(detail.claim.status) }}
        <span v-if="detail.claim.resolution"> — {{ detail.claim.resolution }}</span>
      </p>
      <DataTable
        :columns="lineColumns"
        :rows="detail.lines"
        empty="Aucune ligne contestée."
        empty-testid="no-contested-line"
        :row-testid="(l) => `contested-line-${l.lineId}`"
      />

      <h3 class="ui-detail-sub">Régularisations liées</h3>
      <DataTable
        :columns="adjustmentColumns"
        :rows="detail.linkedAdjustments"
        empty="Aucune régularisation liée."
        empty-testid="no-linked-adjustment"
        :row-testid="(a) => `claim-adjustment-${a.id}`"
      />

      <AppButton
        v-if="canOperate() && detail.claim.status === 'OPEN'"
        variant="secondary"
        data-testid="claim-take"
        @click="take"
      >
        Prendre en charge
      </AppButton>
      <AppForm v-if="canOperate() && detail.claim.status !== 'CLOSED'" @submit="resolve">
        <FormField label="Régularisation" width="long" v-slot="{ id }">
          <input :id="id" data-testid="claim-resolution" type="text" v-model="resolution" />
        </FormField>
        <AppButton type="submit" data-testid="claim-resolve">Clore la réclamation</AppButton>
      </AppForm>
    </AppCard>
  </div>
</template>
