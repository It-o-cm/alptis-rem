import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// Single-page application built into `dist`, served by Quarkus through Quinoa.
export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    // During `quinoa` dev mode the backend is reachable on the same origin;
    // this proxy is used when running the Vite dev server standalone.
    proxy: {
      '/api': 'http://localhost:8080',
      '/q': 'http://localhost:8080',
    },
  },
});
