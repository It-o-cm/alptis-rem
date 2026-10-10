<script setup lang="ts">
import { computed } from 'vue';

/**
 * A page-level message — success, error or warning — shown at the same place on
 * every screen and announced to screen readers (ERGONOMIE §3). Errors are
 * announced assertively; other messages politely.
 */
const props = withDefaults(
  defineProps<{
    /** Kind of message, which drives colour and the live-region politeness. */
    variant?: 'success' | 'error' | 'warning' | 'info';
  }>(),
  { variant: 'info' },
);

/** Tag modifier class matching the mock-up palette. */
const tagClass = computed(
  () =>
    ({ success: 'ok', error: 'ko', warning: 'warn', info: 'info' })[props.variant],
);

/** Assertive for errors so they interrupt, polite otherwise. */
const politeness = computed(() => (props.variant === 'error' ? 'assertive' : 'polite'));
</script>

<template>
  <p class="ui-message tag" :class="tagClass" role="status" :aria-live="politeness" data-ui="message">
    <slot />
  </p>
</template>

<style scoped>
.ui-message {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius);
  font-weight: 600;
  white-space: normal;
}
</style>
