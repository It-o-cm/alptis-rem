<script setup lang="ts">
import { computed } from 'vue';

let seq = 0;

/**
 * A labelled form field: its label, its required mark, its help and its error
 * message always live in the same place (ERGONOMIE §3). The control itself is
 * passed in the default slot, which receives the generated `id` and the
 * `describedby` ids so the label, help and error are properly linked.
 *
 * In grid mode (default) the field renders as `display: contents` so that its
 * label and control become cells of the surrounding {@link AppForm} grid, which
 * keeps every label and every control aligned. In stacked mode the label sits
 * above the control, as used by the filter bar.
 */
const props = withDefaults(
  defineProps<{
    /** French label shown next to or above the control. */
    label: string;
    /** Field id; generated when omitted. */
    id?: string;
    /** Whether the field is required (shows a mark and sets aria-required hint). */
    required?: boolean;
    /** Optional help text shown under the control. */
    help?: string;
    /** Error message shown under the control; empty means no error. */
    error?: string;
    /** Width of the control by nature of its value. */
    width?: 'date' | 'rate' | 'amount' | 'short' | 'long' | 'full';
    /** Stacks the label above the control instead of beside it. */
    stacked?: boolean;
  }>(),
  { id: undefined, required: false, help: undefined, error: undefined, width: 'full', stacked: false },
);

/** Stable field id, provided or generated once. */
const fieldId = props.id ?? `ff-${(seq += 1)}`;
/** Id of the help element, when help is present. */
const helpId = computed(() => (props.help ? `${fieldId}-help` : undefined));
/** Id of the error element, when an error is present. */
const errorId = computed(() => (props.error ? `${fieldId}-error` : undefined));
/** Space-separated ids describing the control, for `aria-describedby`. */
const describedby = computed(() => [helpId.value, errorId.value].filter(Boolean).join(' ') || undefined);
</script>

<template>
  <div class="ui-field" :class="{ 'ui-field--stacked': stacked }" data-ui="field">
    <label class="ui-field__label" :for="fieldId" data-ui="field-label">
      {{ label }}<span v-if="required" class="ui-field__req" aria-hidden="true">&nbsp;*</span>
      <span v-if="required" class="ui-field__sr">(obligatoire)</span>
    </label>
    <div class="ui-field__control" :class="`ui-field__control--${width}`" data-ui="field-control">
      <slot :id="fieldId" :describedby="describedby" />
      <p v-if="help" :id="helpId" class="ui-field__help">{{ help }}</p>
      <p v-if="error" :id="errorId" class="ui-field__error" role="alert">{{ error }}</p>
    </div>
  </div>
</template>

<style scoped>
.ui-field {
  display: contents;
}
.ui-field__label {
  font-size: var(--fs-body);
  color: var(--ink);
  align-self: start;
  padding-top: calc((var(--control-h) - 1.45em) / 2);
}
.ui-field--stacked {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.ui-field--stacked .ui-field__label {
  padding-top: 0;
}
.ui-field__req {
  color: var(--danger);
}
.ui-field__sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
.ui-field__control {
  min-width: 0;
}
.ui-field__control--date {
  max-width: var(--w-date);
}
.ui-field__control--rate {
  max-width: var(--w-rate);
}
.ui-field__control--amount {
  max-width: var(--w-amount);
}
.ui-field__control--short {
  max-width: var(--w-short);
}
.ui-field__control--long {
  max-width: var(--w-long);
}
.ui-field__control :slotted(input),
.ui-field__control :slotted(select),
.ui-field__control :slotted(textarea) {
  width: 100%;
}
.ui-field__help {
  margin: var(--space-1) 0 0;
  font-size: var(--fs-small);
  color: var(--muted);
}
.ui-field__error {
  margin: var(--space-1) 0 0;
  font-size: var(--fs-small);
  color: var(--danger);
}
</style>
