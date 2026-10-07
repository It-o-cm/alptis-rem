<script setup lang="ts">
import { useRoute } from 'vue-router';
import { computed } from 'vue';

// Empty titled page used for every navigation entry at step 0. Each later step
// replaces the placeholder of the screens it delivers with the real screen.
const route = useRoute();
const title = computed(() => String(route.meta.title ?? ''));
</script>

<template>
  <div class="page">
    <div>
      <h1 class="page__title" data-testid="page-title">{{ title }}</h1>
      <div class="page__subtitle">Écran à construire dans son étape du plan.</div>
    </div>
    <div class="card" style="padding: 18px 20px; color: var(--muted)">
      Aucune donnée à afficher pour le moment.
    </div>
  </div>
</template>
