import { defineConfig } from '@playwright/test';

export default defineConfig({
  outputDir: 'test-results/browser-artifacts',
  testDir: '.', testMatch: ['e2e/**/*.spec.ts', 'src/**/*.browser.spec.ts'], fullyParallel: false, workers: 1,
  use: { baseURL: 'http://127.0.0.1:4173', headless: true, viewport: { width: 360, height: 760 }, trace: 'retain-on-failure' },
  webServer: { command: 'npm run dev -- --port 4173 --strictPort', url: 'http://127.0.0.1:4173', reuseExistingServer: !process.env.CI },
  reporter: [['list'], ['junit', { outputFile: 'test-results/e2e.xml' }]],
});
