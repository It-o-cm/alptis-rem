<script setup lang="ts">
import { useRoute } from 'vue-router';
import { computed } from 'vue';
import { AppCard, PageHeading } from '../components/ui';

// Empty titled page used for every navigation entry at step 0. Each later step
// replaces the placeholder of the screens it delivers with the real screen.
const route = useRoute();

/** Title of the current route, read from its navigation metadata. */
const title = computed(() => String(route.meta.title ?? ''));
</script>

<template>
  <div class="page">
    <PageHeading :title="title" subtitle="Écran à construire dans son étape du plan." />
    <AppCard>
      <p class="placeholder-note">Aucune donnée à afficher pour le moment.</p>
    </AppCard>
  </div>
</template>

<style scoped>
.placeholder-note {
  margin: 0;
  color: var(--muted);
}
</style>
