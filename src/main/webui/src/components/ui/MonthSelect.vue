<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { usePeriodsStore } from '../../stores/periods';
import { monthLabel } from '../../presentation/months';
import CodeSelect from './CodeSelect.vue';

/**
 * Month selector fed by the period catalogue (ADM-03): the user picks an accounting month by its
 * French name from a list, never types an ISO code. The list may be restricted to one entity. The
 * currently bound month is always offered, even before the catalogue loads, so the selection holds.
 *
 * The catalogue carries each month as the first-of-month ISO date (`AAAA-MM-01`) the server returns
 * and every month endpoint parses, so the selector binds that date verbatim and only maps it to a
 * French label for display.
 */
const model = defineModel<string>({ required: true });

const props = withDefaults(
  defineProps<{
    /** Restricts the months to one entity; all known months when omitted. */
    entity?: string;
    /** Whether to offer an empty "all months" option. */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { entity: undefined, allowEmpty: false, emptyLabel: 'Tous les mois' },
);

const periods = usePeriodsStore();

onMounted(() => {
  periods.ensure();
});

/** The known months, newest first, always including the currently bound month. */
const options = computed(() => {
  const values = periods.monthsFor(props.entity);
  const withCurrent = model.value && !values.includes(model.value) ? [model.value, ...values] : values;
  return withCurrent.map((v) => ({ value: v, label: monthLabel(v) }));
});
</script>

<template>
  <CodeSelect v-model="model" :options="options" :allow-empty="allowEmpty" :empty-label="emptyLabel" />
</template>
