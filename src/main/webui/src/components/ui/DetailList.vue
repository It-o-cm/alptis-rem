<script setup lang="ts">
/**
 * The detail block of an object: label/value pairs in read mode, laid out as two
 * aligned columns like a form (ERGONOMIE §3). Pairs are passed as
 * {@link DetailRow} children in the default slot.
 */
withDefaults(
  defineProps<{
    /** Flows the pairs into two columns when wide. */
    twoColumn?: boolean;
  }>(),
  { twoColumn: false },
);
</script>

<template>
  <dl class="ui-fiche" :class="{ 'ui-fiche--2col': twoColumn }" data-ui="fiche">
    <slot />
  </dl>
</template>

<style scoped>
.ui-fiche {
  margin: 0;
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: var(--space-3) var(--space-4);
  align-items: baseline;
}
.ui-fiche--2col {
  grid-template-columns: max-content minmax(0, 1fr) max-content minmax(0, 1fr);
}
@media (max-width: 1100px) {
  .ui-fiche--2col {
    grid-template-columns: max-content minmax(0, 1fr);
  }
}
</style>
