import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E configuration for the Angular 21 frontend.
 *
 * Strategy:
 *  - baseURL: the Angular dev server (port 4200).
 *  - webServer: spawn `bun run start` if no server is already running
 *    on :4200. CI starts the backend separately (frontend-ci.yml) and
 *    reuses the local server.
 *  - projects: chromium only for v1 (no firefox/webkit yet).
 *  - timeouts: 30s per test, 0 retries in dev / 2 in CI.
 *  - artifacts: trace + screenshot only on failure to keep PR bundles
 *    small.
 */
const isCI = !!process.env['CI'];

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  reporter: isCI ? [['github'], ['html', { open: 'never' }]] : 'list',

  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: {
    command: 'bun run start',
    url: 'http://localhost:4200',
    reuseExistingServer: !isCI,
    timeout: 120_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
});
