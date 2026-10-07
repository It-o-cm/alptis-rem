<script setup lang="ts">
import { RouterView } from 'vue-router';
import AppNav from './components/AppNav.vue';
import AppHeader from './components/AppHeader.vue';
</script>

<template>
  <div class="app-shell">
    <AppNav />
    <main class="main">
      <AppHeader />
      <RouterView />
    </main>
  </div>
</template>
