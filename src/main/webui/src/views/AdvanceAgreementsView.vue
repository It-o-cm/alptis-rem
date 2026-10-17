<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, DetailList, DetailRow, FormField, PageHeading } from '../components/ui';
import { formatAmount } from '../presentation/format';
import { advanceAgreementStatusLabel, advanceStatusLabel } from '../presentation/advance';
import type { Column } from '../components/ui';

interface AdvanceView {
  entityCode: string; partnerCode: string | null; validFrom: string | null; validTo: string | null;
  anticipationDays: number | null; maxContracts: number | null; status: string | null;
}

interface PositionView {
  coverageCode: string;
  status: string | null;
  advanced: number;
  offset: number;
}

interface AdvanceDetail {
  convention: AdvanceView;
  unpaidRankThreshold: number | null;
  split: string | null;
  products: string[];
  totalAdvanced: number;
  totalOffset: number;
  outstanding: number;
  ruptureAmount: number;
  positions: PositionView[];
}

const session = useSessionStore();
const entityCode = (): string => (session.entities.length > 0 ? session.entities[0] : '');
const canEdit = (): boolean => canEditEntityParameters(session.roles);

const rows = ref<AdvanceView[]>([]);
const detail = ref<AdvanceDetail | null>(null);
const error = ref('');
const message = ref('');
const loading = ref(false);
const form = ref({ partnerCode: '', validFrom: '', validTo: '', anticipationDays: 360, maxContracts: 50, unpaidRankThreshold: 2 });

/** Columns of the advance agreements table. */
const columns: Column<AdvanceView>[] = [
  { key: 'partnerCode', label: 'Distributeur', value: (a) => a.partnerCode ?? '—' },
  { key: 'validFrom', label: 'Du', value: (a) => a.validFrom ?? '—' },
  { key: 'validTo', label: 'Au', value: (a) => a.validTo ?? '—' },
  { key: 'anticipationDays', label: 'Anticipation (j)', align: 'num', value: (a) => a.anticipationDays ?? '—' },
  { key: 'maxContracts', label: 'Plafond', align: 'num', value: (a) => a.maxContracts ?? '—' },
  { key: 'status', label: 'Statut' },
];

/** Columns of the discounted guarantees table. */
const positionColumns: Column<PositionView>[] = [
  { key: 'coverageCode', label: 'Garantie' },
  { key: 'status', label: 'Statut', value: (p) => advanceStatusLabel(p.status) },
  { key: 'advanced', label: 'Escompté', align: 'num', value: (p) => formatAmount(p.advanced) },
  { key: 'offset', label: 'Apuré', align: 'num', value: (p) => formatAmount(p.offset) },
];

/**
 * Opens a convention's fiche (parameters, counters, guarantees, rupture simulation).
 * @param row the selected convention row
 */
async function openDetail(row: AdvanceView): Promise<void> {
  error.value = '';
  try {
    detail.value = await apiGet<AdvanceDetail>(
      `/api/ui/v1/advance-agreements/${row.entityCode}/${row.partnerCode}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

/**
 * Changes the open convention's lifecycle status (PAR-12): suspend or close.
 * @param action the lifecycle action ('suspend' or 'close')
 */
async function changeStatus(action: 'suspend' | 'close'): Promise<void> {
  if (!detail.value || !detail.value.convention.partnerCode) return;
  error.value = '';
  message.value = '';
  const c = detail.value.convention;
  try {
    await apiPost(`/api/ui/v1/advance-agreements/${c.entityCode}/${c.partnerCode}/${action}`, {});
    message.value = action === 'close' ? 'Convention clôturée.' : 'Convention suspendue.';
    await openDetail(c);
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Action refusée';
  }
}

/**
 * Formats an amount in euros.
 * @param value the raw amount
 */
function amount(value: number): string {
  return formatAmount(value);
}

/** Loads the advance agreements of the user's entities. */
async function load(): Promise<void> {
  loading.value = true;
  try {
    rows.value = await apiGet<AdvanceView[]>('/api/ui/v1/advance-agreements');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  } finally {
    loading.value = false;
  }
}

/** Creates an advance agreement from the form and reloads the list. */
async function create(): Promise<void> {
  error.value = '';
  message.value = '';
  try {
    await apiPost('/api/ui/v1/advance-agreements', {
      entityCode: entityCode(), partnerCode: form.value.partnerCode,
      validFrom: form.value.validFrom || null, validTo: form.value.validTo || null,
      anticipationDays: form.value.anticipationDays, maxContracts: form.value.maxContracts,
      unpaidRankThreshold: form.value.unpaidRankThreshold,
    });
    message.value = "Convention d'escompte enregistrée.";
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur';
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Conventions d'escompte"
      subtitle="Anticipation, plafond et rang d'impayé par distributeur (PAR-09)."
    />

    <AppMessage v-if="error" variant="error" data-testid="advance-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="advance-message">{{ message }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty="Aucune convention."
        empty-testid="advance-empty"
        clickable
        :row-testid="(a) => `advance-row-${rows.indexOf(a)}`"
        @row-click="openDetail"
      >
        <template #cell-status="{ row }"><span class="tag ok">{{ advanceAgreementStatusLabel(row.status) }}</span></template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="advance-detail">
      <h2 class="adv-title">Convention — {{ detail.convention.partnerCode ?? '—' }}</h2>
      <DetailList>
        <DetailRow label="Validité">
          {{ (detail.convention.validFrom ?? '—') + ' → ' + (detail.convention.validTo ?? '—') }}
        </DetailRow>
        <DetailRow label="Anticipation (jours)">{{ detail.convention.anticipationDays ?? '—' }}</DetailRow>
        <DetailRow label="Plafond de contrats">{{ detail.convention.maxContracts ?? '—' }}</DetailRow>
        <DetailRow label="Rang d'impayé">{{ detail.unpaidRankThreshold ?? '—' }}</DetailRow>
        <DetailRow label="Partage">{{ detail.split ?? '—' }}</DetailRow>
        <DetailRow label="Produits">{{ detail.products.length > 0 ? detail.products.join(', ') : '—' }}</DetailRow>
        <DetailRow label="Statut">
          <span class="tag" data-testid="adv-status">{{ advanceAgreementStatusLabel(detail.convention.status) }}</span>
        </DetailRow>
      </DetailList>

      <div v-if="canEdit() && detail.convention.status === 'VALID'" class="adv-actions">
        <AppButton variant="secondary" data-testid="adv-suspend" @click="changeStatus('suspend')">Suspendre</AppButton>
        <AppButton variant="secondary" data-testid="adv-close" @click="changeStatus('close')">Clôturer</AppButton>
      </div>
      <div v-else-if="canEdit() && detail.convention.status === 'SUSPENDED'" class="adv-actions">
        <AppButton variant="secondary" data-testid="adv-close" @click="changeStatus('close')">Clôturer</AppButton>
      </div>

      <h3 class="adv-sub">Compteurs</h3>
      <DetailList>
        <DetailRow label="Total escompté"><span data-testid="adv-total-advanced">{{ amount(detail.totalAdvanced) }}</span></DetailRow>
        <DetailRow label="Total apuré">{{ amount(detail.totalOffset) }}</DetailRow>
        <DetailRow label="Créance d'escompte"><span data-testid="adv-outstanding">{{ amount(detail.outstanding) }}</span></DetailRow>
      </DetailList>

      <h3 class="adv-sub">Simulation de rupture</h3>
      <AppMessage variant="info" data-testid="adv-rupture">
        En cas de rupture aujourd'hui, la créance d'escompte à récupérer serait de {{ amount(detail.ruptureAmount) }}.
      </AppMessage>

      <h3 class="adv-sub">Garanties escomptées</h3>
      <DataTable
        :columns="positionColumns"
        :rows="detail.positions"
        empty="Aucune garantie escomptée."
        empty-testid="adv-no-position"
        :row-testid="() => 'adv-position-row'"
      />
    </AppCard>

    <AppCard v-if="canEdit()">
      <AppForm @submit="create">
        <FormField label="Distributeur" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adv-partner" v-model="form.partnerCode" />
        </FormField>
        <FormField label="Du" width="date" v-slot="{ id }">
          <input :id="id" data-testid="adv-from" type="date" v-model="form.validFrom" />
        </FormField>
        <FormField label="Au" width="date" v-slot="{ id }">
          <input :id="id" data-testid="adv-to" type="date" v-model="form.validTo" />
        </FormField>
        <FormField label="Anticipation" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adv-days" type="number" v-model.number="form.anticipationDays" />
        </FormField>
        <FormField label="Plafond" width="short" v-slot="{ id }">
          <input :id="id" data-testid="adv-cap" type="number" v-model.number="form.maxContracts" />
        </FormField>
        <AppButton type="submit" data-testid="adv-create">Enregistrer</AppButton>
      </AppForm>
    </AppCard>
  </div>
</template>

<style scoped>
.adv-title {
  margin: 0;
  font-size: var(--fs-h2);
}
.adv-sub {
  margin: 0;
  font-size: var(--fs-h3);
  font-weight: 600;
}
</style>
