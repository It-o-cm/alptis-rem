<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';

/**
 * The single button of the application, in three levels only (ERGONOMIE §3):
 * a primary action, a secondary action and a dangerous action. When a `to`
 * route is given it renders as a navigation link styled as a button; otherwise
 * it is a real `<button>`. While `loading` it is disabled so a call cannot be
 * fired twice.
 */
const props = withDefaults(
  defineProps<{
    /** Visual level of the action. */
    variant?: 'primary' | 'secondary' | 'danger';
    /** Native button type when rendered as a button. */
    type?: 'button' | 'submit' | 'reset';
    /** Whether the action is disabled. */
    disabled?: boolean;
    /** Whether the action's call is in flight; disables the button. */
    loading?: boolean;
    /** Router target; when set the button renders as a link. */
    to?: string;
  }>(),
  { variant: 'primary', type: 'button', disabled: false, loading: false, to: undefined },
);

const emit = defineEmits<{ (e: 'click', event: MouseEvent): void }>();

/** CSS classes for the chosen level. */
const classes = computed(() => ['btn', { sec: props.variant === 'secondary', danger: props.variant === 'danger' }]);

/** Whether the button is inert (explicitly disabled or mid-call). */
const inert = computed(() => props.disabled || props.loading);

/**
 * Forwards the native click unless the button is inert.
 * @param event the originating mouse event
 */
function onClick(event: MouseEvent): void {
  if (inert.value) {
    return;
  }
  emit('click', event);
}
</script>

<template>
  <RouterLink v-if="to && !inert" :to="to" :class="classes"><slot /></RouterLink>
  <button v-else :class="classes" :type="type" :disabled="inert" :aria-busy="loading" @click="onClick">
    <slot />
  </button>
</template>
