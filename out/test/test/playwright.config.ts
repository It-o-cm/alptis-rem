import { defineConfig, devices } from '@playwright/test';

// The end-to-end suite drives the real application in the `dev` profile, on H2,
// with nothing about the server simulated (TESTS-E2E.md). The dev runner is built
// and launched by run-dev-app.sh; Playwright waits for the backend ping to answer.
const PORT = process.env.E2E_PORT ? Number(process.env.E2E_PORT) : 8099;
const baseURL = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['json', { outputFile: 'test-results/results.json' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `bash run-dev-app.sh ${PORT}`,
    url: `${baseURL}/api/test/ping`,
    reuseExistingServer: !process.env.CI,
    timeout: 240000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
