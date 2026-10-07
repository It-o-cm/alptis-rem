<script setup lang="ts">
import { RouterLink } from 'vue-router';
import { NAV_SECTIONS } from '../presentation/navigation';
</script>

<template>
  <nav class="nav" aria-label="Navigation principale">
    <div class="nav__brand">SI Rémunération</div>
    <template v-for="section in NAV_SECTIONS" :key="section.heading">
      <div class="nav__heading">{{ section.heading }}</div>
      <RouterLink v-for="item in section.items" :key="item.name" :to="item.path">
        {{ item.label }}
      </RouterLink>
    </template>
  </nav>
</template>
