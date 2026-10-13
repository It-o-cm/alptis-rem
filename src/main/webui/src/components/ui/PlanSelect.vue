<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { usePlansStore } from '../../stores/plans';
import CodeSelect from './CodeSelect.vue';

/**
 * Plan selector fed by the plan catalogue (ADM-03): the user picks a commission plan by its name from
 * a list, never types a code. The currently bound code is always offered, even before the catalogue
 * loads, so the selection holds.
 */
const model = defineModel<string>({ required: true });

withDefaults(
  defineProps<{
    /** Whether to offer an empty option (for filters). */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { allowEmpty: false, emptyLabel: 'Tous les plans' },
);

const plans = usePlansStore();

onMounted(() => {
  plans.ensure();
});

/** The known plans, always including the currently bound code. */
const options = computed(() => {
  const list = plans.options;
  if (model.value && !list.some((o) => o.value === model.value)) {
    return [{ value: model.value, label: model.value }, ...list];
  }
  return list;
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
