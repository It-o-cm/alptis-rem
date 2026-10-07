import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

// Light unit tests on Pinia stores and presentation functions, covered at 100%.
// Vue components have no coverage threshold (CLAUDE.md): the end-to-end suite proves them.
export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    coverage: {
      provider: 'v8',
      include: ['src/stores/**/*.ts', 'src/presentation/**/*.ts'],
      thresholds: {
        lines: 100,
        branches: 100,
        functions: 100,
        statements: 100,
      },
    },
  },
});
