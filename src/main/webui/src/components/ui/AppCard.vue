<script setup lang="ts">
/**
 * A padded content card with the mock-up's border and radius, optionally
 * titled. It replaces the ad-hoc `style="padding: …"` cards so spacing comes
 * from the common tokens (ERGONOMIE §3).
 */
withDefaults(
  defineProps<{
    /** Optional card title rendered as a section heading. */
    title?: string;
    /** Removes the inner padding, e.g. when the card only wraps a table. */
    flush?: boolean;
  }>(),
  { title: undefined, flush: false },
);
</script>

<template>
  <section class="card ui-card" :class="{ 'ui-card--flush': flush }" data-ui="card">
    <h2 v-if="title" class="ui-card__title">{{ title }}</h2>
    <slot />
  </section>
</template>

<style scoped>
.ui-card {
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.ui-card--flush {
  padding: 0;
  gap: 0;
}
.ui-card__title {
  margin: 0;
  font-size: var(--fs-h2);
  font-weight: 600;
}
</style>
