import * as dotenv from 'dotenv';
import { defineConfig, devices } from '@playwright/test';
import { nxE2EPreset } from '@nx/playwright/preset';
import { workspaceRoot } from '@nx/devkit';
import path from 'path';

dotenv.config({ path: `./env/.env.${process.env['ENVIRONMENT']}` });

// For CI, you may want to set BASE_URL to the deployed application.
const environment = process.env['ENVIRONMENT'] || 'local';
const baseURL = process.env['PROCESSPUZZLE_TESTBED_BASE_URL'] || 'http://localhost:4200';
export const testConfig = { routePrefix: '/base-entity/samples' };

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
// require('dotenv').config();

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  ...nxE2EPreset(__filename, { testDir: './src' }),
  globalSetup: './src/support/global-setup.ts',
  // Opt out of parallel tests on CI.
  workers: process.env['ENVIRONMENT'] === 'ci' ? 1 : 1,
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    baseURL,
    screenshot: 'only-on-failure',
    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
  },

  /* Run your local dev server before starting the tests */
  webServer: {
    command: 'npm run e2e-test-processpuzzle-testbed',
    reuseExistingServer: (() => {
      if (environment === 'local') return true;
      if (environment === 'dev') return false;
      return environment !== 'ci';
    })(),
    cwd: workspaceRoot,
  },
  reporter: [
    ['html', { outputFolder: path.join(__dirname, 'reports/e2e'), open: 'on-failure' }],
    ['junit', { outputFile: path.join(__dirname, 'reports/e2e/results.xml') }], // combine multiple
  ],
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    // Uncomment for mobile browsers support
    /* {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    /* {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'Mobile Safari',
      use: { ...devices['iPhone 12'] },
    }, */

    // Uncomment for branded browsers
    /* {
      name: 'Microsoft Edge',
      use: { ...devices['Desktop Edge'], channel: 'msedge' },
    },
    {
      name: 'Google Chrome',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    } */
  ],
  testIgnore: [],
});
