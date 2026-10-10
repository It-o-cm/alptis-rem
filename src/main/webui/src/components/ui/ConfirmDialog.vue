<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import AppButton from './AppButton.vue';

/**
 * The single confirmation dialog through which every irreversible action
 * (closing, validating, purging, rejecting) must pass (ERGONOMIE §3). It is a
 * modal: Escape cancels, the confirm button receives focus on opening.
 */
const props = withDefaults(
  defineProps<{
    /** Whether the dialog is shown. */
    open: boolean;
    /** Dialog title. */
    title: string;
    /** Label of the confirm button. */
    confirmLabel?: string;
    /** Label of the cancel button. */
    cancelLabel?: string;
    /** Whether the confirmed action is dangerous (red confirm button). */
    danger?: boolean;
    /** Whether the confirm call is in flight. */
    loading?: boolean;
  }>(),
  { confirmLabel: 'Confirmer', cancelLabel: 'Annuler', danger: false, loading: false },
);

const emit = defineEmits<{ (e: 'confirm'): void; (e: 'cancel'): void }>();

/** Reference to the confirm button, focused when the dialog opens. */
const confirmButton = ref<InstanceType<typeof AppButton> | null>(null);

watch(
  () => props.open,
  async (isOpen) => {
    if (isOpen) {
      await nextTick();
      (confirmButton.value?.$el as HTMLElement | undefined)?.focus();
    }
  },
);

/** Emits the confirmation. */
function onConfirm(): void {
  emit('confirm');
}

/** Emits the cancellation. */
function onCancel(): void {
  emit('cancel');
}
</script>

<template>
  <div v-if="open" class="ui-modal" @keydown.esc="onCancel">
    <div class="ui-modal__backdrop" @click="onCancel" />
    <div
      class="card ui-modal__dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      data-ui="confirm"
      data-testid="confirm-dialog"
    >
      <h2 class="ui-modal__title">{{ title }}</h2>
      <p class="ui-modal__body"><slot /></p>
      <div class="ui-modal__actions">
        <AppButton variant="secondary" data-testid="confirm-cancel" @click="onCancel">{{ cancelLabel }}</AppButton>
        <AppButton
          ref="confirmButton"
          :variant="danger ? 'danger' : 'primary'"
          :loading="loading"
          data-testid="confirm-ok"
          @click="onConfirm"
        >
          {{ confirmLabel }}
        </AppButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ui-modal {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
}
.ui-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(20, 32, 46, 0.45);
}
.ui-modal__dialog {
  position: relative;
  padding: var(--space-6);
  max-width: 28rem;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.ui-modal__title {
  margin: 0;
  font-size: var(--fs-h2);
}
.ui-modal__body {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--muted);
}
.ui-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
