import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './test',
  fullyParallel: true,
  workers: 3,
  outputDir: '../../target/core-browser-results',
  use: { baseURL: 'http://127.0.0.1:4187' },
  projects: ['chromium', 'firefox', 'webkit'].map(browserName => ({
    name: browserName, use: { browserName },
  })),
  webServer: {
    command: 'node server.mjs',
    url: 'http://127.0.0.1:4187',
    reuseExistingServer: false,
  },
});
