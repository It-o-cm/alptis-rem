<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { apiGet, apiPost, ApiError } from '../api/client';
import { useSessionStore } from '../stores/session';
import { canEditEntityParameters } from '../presentation/access';
import {
  AppButton, AppCard, AppForm, AppMessage, CodeSelect, DataTable, DistributorSelect, FormField, PageHeading,
  PlanSelect, RuleSelect,
} from '../components/ui';
import type { Column } from '../components/ui';

interface AgreementView {
  entityCode: string; type: string | null; partnerCode: string | null; groupCode: string | null;
  reference: string; signature: string | null;
}
interface EnrollmentView { partnerCode: string | null; groupCode: string | null; agreementRef: string | null; status: string | null; }

const session = useSessionStore();
const entityCode = () => (session.entities.length > 0 ? session.entities[0] : '');
const canEdit = () => canEditEntityParameters(session.roles);

const agreements = ref<AgreementView[]>([]);
const enrollments = ref<EnrollmentView[]>([]);
const error = ref('');
const message = ref('');
const agForm = ref({ type: 'PARTNERSHIP', partnerCode: '', reference: '', validFrom: '', signed: true });
const enForm = ref({ planCode: '', ruleCode: '', partnerCode: '', agreementRef: '', validFrom: '' });

/** Columns of the agreements table. */
const agreementColumns: Column<AgreementView>[] = [
  { key: 'reference', label: 'Référence' },
  { key: 'type', label: 'Type', value: (a) => a.type ?? '—' },
  { key: 'distributor', label: 'Distributeur', value: (a) => a.partnerCode ?? a.groupCode ?? '—' },
  { key: 'signature', label: 'Signature' },
];

/** Columns of the loaded enrollments table. */
const enrollmentColumns: Column<EnrollmentView>[] = [
  { key: 'partnerCode', label: 'Distributeur', value: (e) => e.partnerCode ?? '—' },
  { key: 'groupCode', label: 'Groupement', value: (e) => e.groupCode ?? '—' },
  { key: 'agreementRef', label: 'Accord', value: (e) => e.agreementRef ?? '—' },
  { key: 'status', label: 'Statut' },
];

/** The loaded agreements as reference options, so an enrollment picks a signed accord from a list (ADM-03). */
const agreementRefOptions = computed(() =>
  agreements.value.map((a) => ({ value: a.reference, label: a.reference })),
);

/**
 * Records a failure, showing its message and clearing any success.
 * @param e the caught error
 */
function fail(e: unknown): void {
  error.value = e instanceof ApiError ? e.message : 'Erreur';
  message.value = '';
}

/**
 * Builds the data-testid of an enrollment row from its position.
 * @param e the enrollment row
 */
function enrollmentTestid(e: EnrollmentView): string {
  return `enrollment-row-${enrollments.value.indexOf(e)}`;
}

/** Loads the founding agreements of the user's entities. */
async function loadAgreements(): Promise<void> {
  try {
    agreements.value = await apiGet<AgreementView[]>('/api/ui/v1/agreements');
  } catch (e) {
    fail(e);
  }
}

/** Creates a founding agreement from the agreement form. */
async function createAgreement(): Promise<void> {
  try {
    await apiPost('/api/ui/v1/agreements', {
      entityCode: entityCode(), type: agForm.value.type, partnerCode: agForm.value.partnerCode || null,
      reference: agForm.value.reference, validFrom: agForm.value.validFrom || null, signed: agForm.value.signed,
    });
    message.value = 'Accord enregistré.';
    error.value = '';
    await loadAgreements();
  } catch (e) {
    fail(e);
  }
}

/** Loads the enrollments of the rule named in the enrollment form. */
async function loadEnrollments(): Promise<void> {
  if (!enForm.value.planCode || !enForm.value.ruleCode) {
    return;
  }
  try {
    enrollments.value = await apiGet<EnrollmentView[]>(
      `/api/ui/v1/plans/${enForm.value.planCode}/rules/${enForm.value.ruleCode}/enrollments?entityCode=${entityCode()}`);
  } catch (e) {
    fail(e);
  }
}

/** Enrolls a distributor into a reserved rule, then reloads the enrollments. */
async function enroll(): Promise<void> {
  try {
    await apiPost(`/api/ui/v1/plans/${enForm.value.planCode}/rules/${enForm.value.ruleCode}/enrollments`, {
      entityCode: entityCode(), partnerCode: enForm.value.partnerCode, agreementRef: enForm.value.agreementRef,
      validFrom: enForm.value.validFrom || null,
    });
    message.value = 'Inscription enregistrée.';
    error.value = '';
    await loadEnrollments();
  } catch (e) {
    fail(e);
  }
}

onMounted(loadAgreements);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Accords et inscriptions"
      subtitle="Accords fondateurs et inscriptions aux règles réservées, actives une fois l'accord signé (PAR-10)."
    />

    <AppMessage v-if="error" variant="error" data-testid="enroll-error">{{ error }}</AppMessage>
    <AppMessage v-if="message" variant="success" data-testid="enroll-message">{{ message }}</AppMessage>

    <AppCard title="Accords">
      <DataTable
        :columns="agreementColumns"
        :rows="agreements"
        row-key="reference"
        empty="Aucun accord."
        empty-testid="agreements-empty"
        :row-testid="(a) => `agreement-row-${a.reference}`"
      >
        <template #cell-reference="{ row }"><span class="mono">{{ row.reference }}</span></template>
        <template #cell-signature="{ row }">
          <span :class="row.signature === 'SIGNED' ? 'tag ok' : 'tag mute'">{{ row.signature ?? '—' }}</span>
        </template>
      </DataTable>

      <AppForm v-if="canEdit()" @submit="createAgreement">
        <FormField label="Référence" width="short" v-slot="{ id }">
          <input :id="id" data-testid="ag-ref" v-model="agForm.reference" />
        </FormField>
        <FormField label="Distributeur" width="short" v-slot="{ id }">
          <DistributorSelect :id="id" data-testid="ag-partner" v-model="agForm.partnerCode" allow-empty />
        </FormField>
        <FormField label="Du" width="date" v-slot="{ id }">
          <input :id="id" data-testid="ag-from" type="date" v-model="agForm.validFrom" />
        </FormField>
        <FormField label="Signé" width="short" v-slot="{ id }">
          <input :id="id" data-testid="ag-signed" type="checkbox" v-model="agForm.signed" />
        </FormField>
        <AppButton type="submit" data-testid="ag-create">Enregistrer l'accord</AppButton>
      </AppForm>
    </AppCard>

    <AppCard v-if="canEdit()" data-testid="enroll-section" title="Inscription à une règle réservée">
      <AppForm @submit="enroll">
        <FormField label="Plan" width="short" v-slot="{ id }">
          <PlanSelect :id="id" data-testid="en-plan" v-model="enForm.planCode" allow-empty empty-label="Choisir un plan" />
        </FormField>
        <FormField label="Règle" width="short" v-slot="{ id }">
          <RuleSelect :id="id" data-testid="en-rule" v-model="enForm.ruleCode" :plan="enForm.planCode" allow-empty empty-label="Choisir une règle" />
        </FormField>
        <FormField label="Distributeur" width="short" v-slot="{ id }">
          <DistributorSelect :id="id" data-testid="en-partner" v-model="enForm.partnerCode" allow-empty />
        </FormField>
        <FormField label="Accord" width="short" v-slot="{ id }">
          <CodeSelect :id="id" data-testid="en-ref" v-model="enForm.agreementRef" :options="agreementRefOptions" allow-empty empty-label="Aucun" />
        </FormField>
        <FormField label="Du" width="date" v-slot="{ id }">
          <input :id="id" data-testid="en-from" type="date" v-model="enForm.validFrom" />
        </FormField>
        <AppButton variant="secondary" type="submit" data-testid="en-enroll">Inscrire</AppButton>
        <AppButton variant="secondary" type="button" data-testid="en-load" @click="loadEnrollments">
          Voir les inscriptions
        </AppButton>
      </AppForm>

      <DataTable
        :columns="enrollmentColumns"
        :rows="enrollments"
        empty="Aucune inscription chargée."
        empty-testid="enrollments-empty"
        :row-testid="enrollmentTestid"
      >
        <template #cell-status="{ row }">
          <span :class="row.status === 'ACTIVE' ? 'tag ok' : 'tag mute'">{{ row.status ?? '—' }}</span>
        </template>
      </DataTable>
    </AppCard>
  </div>
</template>
