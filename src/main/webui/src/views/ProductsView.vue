<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { AppButton, AppCard, AppForm, AppMessage, DataTable, FormField, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';
import { useSessionStore } from '../stores/session';
import { canManageReferential } from '../presentation/access';

interface ProductView {
  entityCode: string;
  code: string;
  label: string;
  rangeCode: string | null;
  insurerCode: string | null;
  commissionable: boolean;
  validFrom: string | null;
  validTo: string | null;
}

interface InsurerView { code: string; name: string; role: string | null; validFrom: string | null; validTo: string | null; }
interface RangeView { code: string; label: string; target: string | null; validFrom: string | null; validTo: string | null; }
interface OptionView { productCode: string; code: string; label: string; optionType: string | null; validFrom: string | null; validTo: string | null; }

const session = useSessionStore();
const products = ref<ProductView[]>([]);
const insurers = ref<InsurerView[]>([]);
const ranges = ref<RangeView[]>([]);
const options = ref<OptionView[]>([]);
const error = ref('');
const message = ref('');
const loading = ref(false);
const saving = ref(false);

const emptyCreate = {
  type: 'insurer', code: '', label: '', validFrom: '', validTo: '',
  role: '', target: '', optionType: '', productCode: '', rangeCode: '', insurerCode: '', entityCode: '',
};
const create = ref({ ...emptyCreate });
const end = ref({ type: 'insurer', code: '', validTo: '', productCode: '', entityCode: '' });

/** Whether the current user may manage the referential. */
const canManage = (): boolean => canManageReferential(session.roles);

/** The entity used for a product by default (the user's first visible entity). */
const defaultEntity = (): string => (session.entities.length > 0 ? session.entities[0] : '');

/** Validity columns shared by the referential tables. */
function validity<T extends { validFrom: string | null; validTo: string | null }>(): Column<T>[] {
  return [
    { key: 'validFrom', label: 'Validité début', value: (r) => r.validFrom ?? '—' },
    { key: 'validTo', label: 'Validité fin', value: (r) => r.validTo ?? '—' },
  ];
}

/** Columns of the products list. */
const columns: Column<ProductView>[] = [
  { key: 'entityCode', label: 'Entité' },
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'rangeCode', label: 'Gamme', value: (p) => p.rangeCode ?? '—' },
  { key: 'insurerCode', label: 'Assureur', value: (p) => p.insurerCode ?? '—' },
  { key: 'commissionable', label: 'Commissionnable' },
  ...validity<ProductView>(),
];

/** Columns of the insurers list. */
const insurerColumns: Column<InsurerView>[] = [
  { key: 'code', label: 'Code' },
  { key: 'name', label: 'Nom' },
  { key: 'role', label: 'Rôle', value: (i) => i.role ?? '—' },
  ...validity<InsurerView>(),
];

/** Columns of the ranges list. */
const rangeColumns: Column<RangeView>[] = [
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'target', label: 'Cible', value: (r) => r.target ?? '—' },
  ...validity<RangeView>(),
];

/** Columns of the options list. */
const optionColumns: Column<OptionView>[] = [
  { key: 'productCode', label: 'Produit' },
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'optionType', label: 'Type', value: (o) => o.optionType ?? '—' },
  ...validity<OptionView>(),
];

/** Loads the insurers, ranges, products and options of the user's entities. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    insurers.value = await apiGet<InsurerView[]>('/api/ui/v1/insurers');
    ranges.value = await apiGet<RangeView[]>('/api/ui/v1/ranges');
    products.value = await apiGet<ProductView[]>('/api/ui/v1/products');
    options.value = await apiGet<OptionView[]>('/api/ui/v1/options');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Erreur de chargement';
  } finally {
    loading.value = false;
  }
}

/** Builds the create payload and endpoint path for the chosen object type. */
function createRequest(): { path: string; body: Record<string, string | null> } {
  const f = create.value;
  const to = f.validTo || null;
  if (f.type === 'range') {
    return { path: '/ranges', body: { code: f.code, label: f.label, target: f.target || null, validFrom: f.validFrom, validTo: to } };
  }
  if (f.type === 'option') {
    return { path: '/options', body: { productCode: f.productCode, code: f.code, label: f.label, optionType: f.optionType || null, validFrom: f.validFrom, validTo: to } };
  }
  if (f.type === 'product') {
    return { path: '/products', body: { entityCode: f.entityCode || defaultEntity(), code: f.code, label: f.label, rangeCode: f.rangeCode || null, insurerCode: f.insurerCode || null, validFrom: f.validFrom, validTo: to } };
  }
  return { path: '/insurers', body: { code: f.code, name: f.label, role: f.role || null, validFrom: f.validFrom, validTo: to } };
}

/** Creates a referential object with a validity window (REF-01, INT-24). */
async function submitCreate(): Promise<void> {
  error.value = '';
  message.value = '';
  saving.value = true;
  try {
    const { path, body } = createRequest();
    await apiPost(`/api/ui/v1${path}`, body);
    message.value = `Objet ${create.value.code} créé.`;
    create.value = { ...emptyCreate, type: create.value.type };
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Création refusée';
  } finally {
    saving.value = false;
  }
}

/** Ends the validity of a referential object (REF-01, INT-24). */
async function submitEnd(): Promise<void> {
  error.value = '';
  message.value = '';
  saving.value = true;
  try {
    const f = end.value;
    await apiPost(`/api/ui/v1/referentials/${f.type}/${f.code}/end`, {
      productCode: f.productCode || null,
      entityCode: f.entityCode || (f.type === 'product' ? defaultEntity() : null),
      validTo: f.validTo,
    });
    message.value = `Validité de ${f.code} terminée au ${f.validTo}.`;
    end.value = { type: f.type, code: '', validTo: '', productCode: '', entityCode: '' };
    await load();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Fin de validité refusée';
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading title="Assureurs, gammes, produits et options" subtitle="Le référentiel produit par entité (REF-01)." />

    <AppMessage v-if="error" variant="error" data-testid="load-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="ref-message">{{ message }}</AppMessage>

    <AppCard v-if="canManage()" data-testid="create-form" title="Créer un objet de référentiel"
             subtitle="Un assureur, une gamme, une option ou un produit, avec ses dates de validité. Un chevauchement de validité est refusé (REF-01).">
      <AppForm @submit="submitCreate">
        <FormField label="Type" width="short" v-slot="{ id }">
          <select :id="id" data-testid="create-type" v-model="create.type">
            <option value="insurer">Assureur</option>
            <option value="range">Gamme</option>
            <option value="option">Option</option>
            <option value="product">Produit</option>
          </select>
        </FormField>
        <FormField label="Code" width="short" v-slot="{ id }">
          <input :id="id" data-testid="create-code" type="text" v-model="create.code" />
        </FormField>
        <FormField label="Libellé / Nom" width="short" v-slot="{ id }">
          <input :id="id" data-testid="create-label" type="text" v-model="create.label" />
        </FormField>
        <FormField label="Validité début" width="short" v-slot="{ id }">
          <input :id="id" data-testid="create-valid-from" type="date" v-model="create.validFrom" />
        </FormField>
        <FormField label="Validité fin" width="short" v-slot="{ id }">
          <input :id="id" data-testid="create-valid-to" type="date" v-model="create.validTo" />
        </FormField>
        <FormField v-if="create.type === 'option' || create.type === 'product'" label="Produit / Entité" width="short" v-slot="{ id }">
          <input :id="id" data-testid="create-parent" type="text" v-model="create.productCode"
                 v-if="create.type === 'option'" placeholder="Code produit" />
          <input :id="id" data-testid="create-parent" type="text" v-model="create.entityCode"
                 v-else placeholder="Code entité (défaut : la vôtre)" />
        </FormField>
        <AppButton type="submit" :loading="saving" data-testid="create-submit">Créer</AppButton>
      </AppForm>
    </AppCard>

    <AppCard v-if="canManage()" data-testid="end-form" title="Terminer une validité"
             subtitle="Avancer la date de fin de validité d'un objet (REF-01).">
      <AppForm @submit="submitEnd">
        <FormField label="Type" width="short" v-slot="{ id }">
          <select :id="id" data-testid="end-type" v-model="end.type">
            <option value="insurer">Assureur</option>
            <option value="range">Gamme</option>
            <option value="option">Option</option>
            <option value="product">Produit</option>
          </select>
        </FormField>
        <FormField label="Code" width="short" v-slot="{ id }">
          <input :id="id" data-testid="end-code" type="text" v-model="end.code" />
        </FormField>
        <FormField label="Date de fin" width="short" v-slot="{ id }">
          <input :id="id" data-testid="end-valid-to" type="date" v-model="end.validTo" />
        </FormField>
        <FormField v-if="end.type === 'option'" label="Code produit" width="short" v-slot="{ id }">
          <input :id="id" data-testid="end-product" type="text" v-model="end.productCode" />
        </FormField>
        <AppButton type="submit" :loading="saving" data-testid="end-submit">Terminer</AppButton>
      </AppForm>
    </AppCard>

    <AppCard title="Assureurs" data-testid="insurers">
      <DataTable
        :columns="insurerColumns"
        :rows="insurers"
        empty="Aucun assureur."
        empty-testid="no-insurer"
        :row-testid="(i) => `insurer-row-${i.code}`"
      />
    </AppCard>

    <AppCard title="Gammes" data-testid="ranges">
      <DataTable
        :columns="rangeColumns"
        :rows="ranges"
        empty="Aucune gamme."
        empty-testid="no-range"
        :row-testid="(r) => `range-row-${r.code}`"
      />
    </AppCard>

    <AppCard title="Produits" flush>
      <DataTable
        :columns="columns"
        :rows="products"
        :loading="loading"
        :row-key="(p) => p.entityCode + p.code"
        empty="Aucun produit."
        empty-testid="empty"
        :row-testid="(p) => `product-row-${p.code}`"
      >
        <template #cell-code="{ row }"><span class="mono">{{ row.code }}</span></template>
        <template #cell-commissionable="{ row }">
          <span :class="row.commissionable ? 'tag ok' : 'tag mute'">{{ row.commissionable ? 'Oui' : 'Non' }}</span>
        </template>
      </DataTable>
    </AppCard>

    <AppCard title="Options" data-testid="options">
      <DataTable
        :columns="optionColumns"
        :rows="options"
        empty="Aucune option."
        empty-testid="no-option"
        :row-testid="(o) => `option-row-${o.code}`"
      />
    </AppCard>
  </div>
</template>
