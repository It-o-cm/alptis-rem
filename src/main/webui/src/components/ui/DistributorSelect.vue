<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useDistributorsStore } from '../../stores/distributors';
import CodeSelect from './CodeSelect.vue';

/**
 * Distributor selector fed by the distributor catalogue (ADM-03): the user picks a distributor by
 * its name from a list, never types a partner code. The currently bound code is always offered,
 * even before the catalogue loads, so the selection holds.
 */
const model = defineModel<string>({ required: true });

withDefaults(
  defineProps<{
    /** Whether to offer an empty "all distributors" option (for filters). */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { allowEmpty: false, emptyLabel: 'Tous les distributeurs' },
);

const distributors = useDistributorsStore();

onMounted(() => {
  distributors.ensure();
});

/** The known distributors, always including the currently bound code. */
const options = computed(() => {
  const list = distributors.options;
  if (model.value && !list.some((o) => o.value === model.value)) {
    return [{ value: model.value, label: model.value }, ...list];
  }
  return list;
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
