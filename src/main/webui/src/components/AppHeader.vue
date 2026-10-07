<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useSessionStore } from '../stores/session';
import { NAV_SECTIONS } from '../presentation/navigation';

const route = useRoute();
const session = useSessionStore();

// Heading of the section the current route belongs to, shown next to the entity.
const sectionHeading = computed(() => {
  const match = NAV_SECTIONS.find((section) =>
    section.items.some((item) => item.name === route.name),
  );
  return match ? match.heading : '';
});
</script>

<template>
  <header class="header">
    <div class="header__scope" data-testid="header-scope">
      {{ session.entityName }}<span v-if="sectionHeading"> · {{ sectionHeading }}</span>
    </div>
    <div class="header__right">
      <span class="tag mute">Données fictives</span>
      <span data-testid="header-role">Rôle : {{ session.roleLabelText }}</span>
    </div>
  </header>
</template>
