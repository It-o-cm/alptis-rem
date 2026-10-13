<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useDelegationsStore } from '../../stores/delegations';
import CodeSelect from './CodeSelect.vue';

/**
 * Delegation selector fed by the delegation catalogue (ADM-03): the user picks a sales delegation
 * from a list, never types one. The currently bound value is always offered, even before the
 * catalogue loads, so the selection holds.
 */
const model = defineModel<string>({ required: true });

withDefaults(
  defineProps<{
    /** Whether to offer an empty "all delegations" option (for filters). */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { allowEmpty: false, emptyLabel: 'Toutes les délégations' },
);

const delegations = useDelegationsStore();

onMounted(() => {
  delegations.ensure();
});

/** The known delegations, always including the currently bound value. */
const options = computed(() => {
  const list = delegations.options;
  if (model.value && !list.some((o) => o.value === model.value)) {
    return [{ value: model.value, label: model.value }, ...list];
  }
  return list;
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
