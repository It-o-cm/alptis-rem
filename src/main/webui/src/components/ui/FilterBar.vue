<script setup lang="ts">
import AppButton from './AppButton.vue';

/**
 * The search-criteria bar shown above a list, with a single presentation and the
 * "Rechercher" and "Réinitialiser" actions always in the same place (ERGONOMIE
 * §3). The criteria are passed in the default slot as stacked {@link FormField}s.
 */
withDefaults(
  defineProps<{
    /** Whether a search is in flight; disables the search button. */
    loading?: boolean;
    /** Hides the reset action when a screen has nothing to reset. */
    resettable?: boolean;
  }>(),
  { loading: false, resettable: true },
);

const emit = defineEmits<{ (e: 'search'): void; (e: 'reset'): void }>();

/** Fires the search action. */
function onSearch(): void {
  emit('search');
}

/** Fires the reset action. */
function onReset(): void {
  emit('reset');
}
</script>

<template>
  <form class="card ui-filterbar" data-ui="filterbar" @submit.prevent="onSearch">
    <div class="ui-filterbar__fields">
      <slot />
    </div>
    <div class="ui-filterbar__actions">
      <AppButton type="submit" :loading="loading" data-testid="filter-search">Rechercher</AppButton>
      <AppButton v-if="resettable" variant="secondary" type="button" data-testid="filter-reset" @click="onReset">
        Réinitialiser
      </AppButton>
    </div>
  </form>
</template>

<style scoped>
.ui-filterbar {
  padding: var(--space-5);
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-4);
}
.ui-filterbar__fields {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  flex: 1 1 auto;
}
.ui-filterbar__actions {
  display: flex;
  gap: var(--space-2);
  align-items: flex-end;
}
</style>
