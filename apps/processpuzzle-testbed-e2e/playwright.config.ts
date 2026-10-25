import * as dotenv from 'dotenv';
import { defineConfig, devices } from '@playwright/test';
import { nxE2EPreset } from '@nx/playwright/preset';
import { workspaceRoot } from '@nx/devkit';
import path from 'path';
import { STORAGE_STATE } from './src/support/auth.paths';

// For CI, you may want to set BASE_URL to the deployed application.
const environment = process.env['ENVIRONMENT'] || 'dev';
dotenv.config({ path: path.join(__dirname, 'env', `.env.${environment}`) });
const baseURL = process.env['PROCESSPUZZLE_TESTBED_BASE_URL'] || 'http://localhost:4200';
export const testConfig = { routePrefix: '/base-entity/samples' };

const escapeRegex = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const entityUnderTest = process.env['E2E_ENTITY'];
const suiteUnderTest = process.env['E2E_SUITE']; // CRUD or LIST
const grep =
  entityUnderTest || suiteUnderTest ? new RegExp([entityUnderTest ? `\\[${escapeRegex(entityUnderTest)}\\]` : '', suiteUnderTest ? ` ${escapeRegex(suiteUnderTest)}` : ''].join('')) : undefined;

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  ...nxE2EPreset(__filename, { testDir: './src' }),
  globalSetup: './src/support/global-setup.ts',
  // Was built but never handed to Playwright, which left E2E_ENTITY / E2E_SUITE with no effect at all.
  grep,
  // Spec files run side by side, one worker each; the tests inside a file stay in order (the generated suites build
  // on each other's data). One by default, which is what a developer's machine and a deployed stage want.
  workers: Number(process.env['E2E_WORKERS'] ?? 1),
  // A single retry on CI: the suite drives a freshly started stack, and a first request that lands before the backend has warmed up is not a regression.
  retries: process.env['CI'] ? 1 : 0,
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    baseURL,
    screenshot: 'only-on-failure',
    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
  },

  /*
   * Run your local dev server before starting the tests — for `dev` ONLY.
   *
   * `ci` targets the containerised stack on :9090 and `stage` / `prod` a deployed environment, and in
   * none of the three can this command produce the `url` Playwright then waits for: it serves :4200.
   * Left unconditional, a deployment that is down turns into `npm run serve-…` followed by a 60 s wait
   * on an unrelated host — a timeout three layers from its cause, where the honest report is "the
   * deployed frontend did not answer". Which is exactly what the post-deploy gate in
   * .github/workflows/deploy-testbed-apps.yml needs it to say.
   */
  webServer:
    environment === 'dev'
      ? {
          command: 'npm run serve-processpuzzle-testbed-frontend',
          url: baseURL,
          reuseExistingServer: true,
          cwd: workspaceRoot,
        }
      : undefined,
  reporter: [
    ['html', { outputFolder: path.join(__dirname, 'reports/e2e'), open: 'on-failure' }],
    ['junit', { outputFile: path.join(__dirname, 'reports/e2e/results.xml') }], // combine multiple
  ],
  projects: [
    // Registers and signs in a test user once; only the `@auth` project depends on it, so the anonymous suites
    // (and the prod smoke run) neither need sign-up to be open nor change behaviour.
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'chromium',
      grepInvert: /@auth/,
      use: { ...devices['Desktop Chrome'] },
    },
    // Specs that need a signed-in user are tagged `@auth` in their describe title.
    {
      name: 'chromium-auth',
      grep: /@auth/,
      dependencies: ['setup'],
      use: { ...devices['Desktop Chrome'], storageState: STORAGE_STATE },
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
