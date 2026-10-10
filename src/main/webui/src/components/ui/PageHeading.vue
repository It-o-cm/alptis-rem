<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { sectionHeadingOf } from '../../presentation/navigation';

/**
 * The common heading of every screen (ERGONOMIE §2.3): the name of the screen's
 * navigation rubric sits above the page title, identically everywhere. An
 * optional subtitle follows.
 */
defineProps<{
  /** The page title shown in the big heading. */
  title: string;
  /** Optional one-line subtitle under the title. */
  subtitle?: string;
}>();

const route = useRoute();

/** The rubric (navigation section heading) the current screen belongs to. */
const rubric = computed(() => sectionHeadingOf(route.name as string | undefined));
</script>

<template>
  <div class="ui-heading" data-ui="heading">
    <p v-if="rubric" class="ui-heading__rubric" data-testid="page-rubric">{{ rubric.toUpperCase() }}</p>
    <h1 class="page__title" data-testid="page-title">{{ title }}</h1>
    <p v-if="subtitle" class="page__subtitle">{{ subtitle }}</p>
  </div>
</template>

<style scoped>
.ui-heading__rubric {
  margin: 0 0 var(--space-1);
  font-size: var(--fs-eyebrow);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--muted);
  font-weight: 600;
}
.ui-heading .page__subtitle {
  margin-top: var(--space-1);
}
</style>
