<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useSourceSystemsStore } from '../../stores/sourceSystems';
import CodeSelect from './CodeSelect.vue';

/**
 * Source system selector fed by the source system catalogue (ADM-03): the user picks the system
 * from a list, never types a code. The currently bound value is always offered, even before the
 * catalogue loads, so the selection holds — this also keeps a manual-entry value such as `MANUAL`
 * available when it is not part of the server catalogue.
 */
const model = defineModel<string>({ required: true });

withDefaults(
  defineProps<{
    /** Whether to offer an empty "all systems" option (for filters). */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { allowEmpty: false, emptyLabel: 'Tous les systèmes' },
);

const systems = useSourceSystemsStore();

onMounted(() => {
  systems.ensure();
});

/** The known source systems, always including the currently bound value. */
const options = computed(() => {
  const list = systems.options;
  if (model.value && !list.some((o) => o.value === model.value)) {
    return [{ value: model.value, label: model.value }, ...list];
  }
  return list;
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
