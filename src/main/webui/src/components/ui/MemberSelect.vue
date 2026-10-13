<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useProductionCodesStore } from '../../stores/productionCodes';
import CodeSelect from './CodeSelect.vue';

/**
 * Production-code selector fed by the production-code catalogue (ADM-03): the user picks a member's
 * production code from a list, never types it. The currently bound code is always offered, even
 * before the catalogue loads, so the selection holds.
 */
const model = defineModel<string>({ required: true });

withDefaults(
  defineProps<{
    /** Whether to offer an empty option (for filters). */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { allowEmpty: false, emptyLabel: 'Tous les codes' },
);

const codes = useProductionCodesStore();

onMounted(() => {
  codes.ensure();
});

/** The known production codes, always including the currently bound code. */
const options = computed(() => {
  const list = codes.options;
  if (model.value && !list.some((o) => o.value === model.value)) {
    return [{ value: model.value, label: model.value }, ...list];
  }
  return list;
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
