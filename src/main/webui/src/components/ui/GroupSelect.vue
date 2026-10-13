<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useGroupingsStore } from '../../stores/groupings';
import CodeSelect from './CodeSelect.vue';

/**
 * Grouping selector fed by the grouping catalogue (ADM-03): the user picks a distributor grouping by
 * its name from a list, never types a code. The currently bound code is always offered, even before
 * the catalogue loads, so the selection holds.
 */
const model = defineModel<string>({ required: true });

withDefaults(
  defineProps<{
    /** Whether to offer an empty "all groupings" option (for filters). */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { allowEmpty: false, emptyLabel: 'Tous les groupements' },
);

const groupings = useGroupingsStore();

onMounted(() => {
  groupings.ensure();
});

/** The known groupings, always including the currently bound code. */
const options = computed(() => {
  const list = groupings.options;
  if (model.value && !list.some((o) => o.value === model.value)) {
    return [{ value: model.value, label: model.value }, ...list];
  }
  return list;
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
