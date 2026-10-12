<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiGet, ApiError } from '../api/client';
import { AppButton, AppCard, AppMessage, DataTable, PageHeading } from '../components/ui';
import type { Column } from '../components/ui';

interface GroupView {
  code: string;
  label: string;
  headCode: string | null;
  headName: string | null;
  memberCount: number;
}

interface MemberView {
  partnerCode: string;
  legalName: string;
  productionCodes: string[];
}

interface GroupDetail {
  group: GroupView;
  members: MemberView[];
}

const groups = ref<GroupView[]>([]);
const detail = ref<GroupDetail | null>(null);
const error = ref('');
const loading = ref(false);

/** Columns of the groupings list. */
const columns: Column<GroupView>[] = [
  { key: 'code', label: 'Code' },
  { key: 'label', label: 'Libellé' },
  { key: 'headName', label: 'Distributeur tête', value: (g) => g.headName ?? g.headCode ?? '—' },
  { key: 'memberCount', label: 'Membres', align: 'num', value: (g) => String(g.memberCount) },
];

/** Columns of the members table. */
const memberColumns: Column<MemberView>[] = [
  { key: 'partnerCode', label: 'Code partenaire' },
  { key: 'legalName', label: 'Nom' },
  { key: 'productionCodes', label: 'Codes de production', value: (m) => m.productionCodes.join(', ') || '—' },
];

/** Loads the groupings. */
async function load(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    groups.value = await apiGet<GroupView[]>('/api/ui/v1/groups');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Chargement impossible';
  } finally {
    loading.value = false;
  }
}

/** Opens a grouping's members. */
async function open(g: GroupView): Promise<void> {
  detail.value = await apiGet<GroupDetail>(`/api/ui/v1/groups/${g.code}`);
}

onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeading
      title="Groupements"
      subtitle="Les groupements de distributeurs et leurs membres, avec leurs codes de production (CPT-02, REF-03)."
    />

    <AppMessage v-if="error" variant="error" data-testid="groups-error">{{ error }}</AppMessage>

    <AppCard flush>
      <DataTable
        :columns="columns"
        :rows="groups"
        :loading="loading"
        empty="Aucun groupement."
        empty-testid="no-group"
        :row-testid="(g) => `group-row-${g.code}`"
      >
        <template #actions="{ row }">
          <AppButton variant="secondary" :data-testid="`open-group-${row.code}`" @click="open(row)">Membres</AppButton>
        </template>
      </DataTable>
    </AppCard>

    <AppCard v-if="detail" data-testid="group-detail" :title="`Membres de ${detail.group.label}`">
      <DataTable
        :columns="memberColumns"
        :rows="detail.members"
        empty="Aucun membre."
        empty-testid="no-member"
        :row-testid="(m) => `member-row-${m.partnerCode}`"
      />
    </AppCard>
  </div>
</template>
