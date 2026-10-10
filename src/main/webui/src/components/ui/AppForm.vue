<script setup lang="ts">
/**
 * A form laying out its {@link FormField} children as two aligned columns: the
 * labels in one column, the controls in another, aligned over the whole height
 * (ERGONOMIE §3). When `twoColumn` is set and the viewport is wide enough the
 * fields flow into two pairs of columns, falling back to one on a narrow
 * screen. Submitting the form emits `submit`.
 */
withDefaults(
  defineProps<{
    /** Lays the fields out as two pairs of label/control columns when wide. */
    twoColumn?: boolean;
  }>(),
  { twoColumn: false },
);

const emit = defineEmits<{ (e: 'submit'): void }>();

/** Emits the form submission, preventing the native page reload. */
function onSubmit(): void {
  emit('submit');
}
</script>

<template>
  <form class="ui-form" :class="{ 'ui-form--2col': twoColumn }" data-ui="form" @submit.prevent="onSubmit">
    <slot />
  </form>
</template>

<style scoped>
.ui-form {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: var(--space-4) var(--space-4);
  align-items: start;
}
.ui-form--2col {
  grid-template-columns: max-content minmax(0, 1fr) max-content minmax(0, 1fr);
}
@media (max-width: 1100px) {
  .ui-form--2col {
    grid-template-columns: max-content minmax(0, 1fr);
  }
}
</style>
