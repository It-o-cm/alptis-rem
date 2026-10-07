<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { NAV_SECTIONS } from '../presentation/navigation';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();

// Sections with only the entries the current user may see; empty sections are dropped.
const visibleSections = computed(() =>
  NAV_SECTIONS.map((section) => ({
    heading: section.heading,
    items: section.items.filter((item) => session.canSee(item.name)),
  })).filter((section) => section.items.length > 0),
);
</script>

<template>
  <nav class="nav" aria-label="Navigation principale">
    <div class="nav__brand">SI Rémunération</div>
    <template v-for="section in visibleSections" :key="section.heading">
      <div class="nav__heading">{{ section.heading }}</div>
      <RouterLink v-for="item in section.items" :key="item.name" :to="item.path">
        {{ item.label }}
      </RouterLink>
    </template>
  </nav>
</template>
