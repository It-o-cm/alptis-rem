<script setup lang="ts">
/**
 * The single-choice selector of the socle (ERGONOMIE §3, ADM-03): a native `<select>` over a list
 * of (value, label) options, so a known value is always picked from a list instead of typed as a
 * code. The chosen value is two-way bound through `v-model`. An optional empty option heads the
 * list for filters that allow "all". The root is the `<select>` itself, so an `id` or `data-testid`
 * set by the caller falls through to it.
 */
const model = defineModel<string>({ required: true });

withDefaults(
  defineProps<{
    /** The choices offered, each a value kept by the model and a French label shown. */
    options: ReadonlyArray<{ value: string; label: string }>;
    /** Whether to offer an empty "all" option at the head of the list. */
    allowEmpty?: boolean;
    /** Label of the empty option when offered. */
    emptyLabel?: string;
  }>(),
  { allowEmpty: false, emptyLabel: 'Tous' },
);
</script>

<template>
  <select v-model="model">
    <option v-if="allowEmpty" value="">{{ emptyLabel }}</option>
    <option v-for="o in options" :key="o.value" :value="o.value">{{ o.label }}</option>
  </select>
</template>
