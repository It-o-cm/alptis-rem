<script setup lang="ts">
/**
 * One label/value pair of a {@link DetailList}. It renders as `display: contents`
 * so its term and definition become cells of the surrounding grid, keeping all
 * labels and all values aligned.
 */
defineProps<{
  /** French label of the pair. */
  label: string;
}>();
</script>

<template>
  <div class="ui-fiche__row" data-ui="fiche-row">
    <dt class="ui-fiche__label" data-ui="fiche-label">{{ label }}</dt>
    <dd class="ui-fiche__value" data-ui="fiche-value"><slot /></dd>
  </div>
</template>

<style scoped>
.ui-fiche__row {
  display: contents;
}
.ui-fiche__label {
  font-size: var(--fs-body);
  color: var(--muted);
}
.ui-fiche__value {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--ink);
}
</style>
