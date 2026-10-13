<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRulesStore } from '../../stores/rules';
import CodeSelect from './CodeSelect.vue';

/**
 * Rule selector fed by the rule catalogue (ADM-03): the user picks a rule by its name from a list,
 * never types a code. When a plan is given, the list is restricted to that plan's rules. The
 * currently bound code is always offered, even before the catalogue loads, so the selection holds.
 */
const model = defineModel<string>({ required: true });

const props = withDefaults(
  defineProps<{
    /** Restricts the rules to one plan; all rules when omitted. */
    plan?: string;
    /** Whether to offer an empty option (for filters). */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { plan: undefined, allowEmpty: false, emptyLabel: 'Toutes les règles' },
);

const rules = useRulesStore();

onMounted(() => {
  rules.ensure();
});

/** The known rules of the given plan, always including the currently bound code. */
const options = computed(() => {
  const list = rules.optionsFor(props.plan);
  if (model.value && !list.some((o) => o.value === model.value)) {
    return [{ value: model.value, label: model.value }, ...list];
  }
  return list;
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
