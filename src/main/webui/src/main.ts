import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { router } from './router';
import { useSessionStore } from './stores/session';
import './styles.css';

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);

// Load the current user before the first paint so the header and navigation reflect the role.
useSessionStore(pinia)
  .load()
  .finally(() => app.mount('#app'));
