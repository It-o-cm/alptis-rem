<script setup lang="ts">
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
</script>

<template>
  <header class="header">
    <div class="header__scope" data-testid="header-scope">{{ session.primaryEntityName }}</div>
    <div class="header__right">
      <span class="tag mute">Données fictives</span>
      <span v-if="session.authenticated" data-testid="header-role">Rôle : {{ session.roleLabelText }}</span>
      <span v-else data-testid="header-role">Non connecté</span>
    </div>
  </header>
</template>
