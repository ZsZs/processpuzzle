import * as dotenv from 'dotenv';
import { defineConfig, devices } from '@playwright/test';
import { nxE2EPreset } from '@nx/playwright/preset';
import { workspaceRoot } from '@nx/devkit';
import path from 'path';
import { authFilesFor } from './src/support/auth.paths';

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
  projects: (() => {
    // When E2E_BROWSERS is set (e.g. "chromium,firefox,webkit,edge"), generate projects for all
    // requested browsers. When unset, fall back to chromium-only for backward compatibility with
    // existing CI workflows.
    const browsersEnv = process.env['E2E_BROWSERS'];
    const browsers = browsersEnv
      ? browsersEnv.split(',').map((b) => b.trim().toLowerCase())
      : ['chromium'];

    // Every browser signs in itself (`setup-<browser>`, below). A session made in Chromium and handed to Firefox or
    // WebKit is not restored by them — the header stays anonymous and every @auth test fails — so the browser
    // matrix would only ever test signed-in behaviour in Chromium.
    const projects: any[] = [];

    // Browser device mapping
    const deviceMap: Record<string, any> = {
      chromium: devices['Desktop Chrome'],
      firefox: devices['Desktop Firefox'],
      webkit: devices['Desktop Safari'],
      edge: { ...devices['Desktop Edge'], channel: 'msedge' },
    };

    for (const browser of browsers) {
      const device = deviceMap[browser];
      if (!device) continue;

      // The recognize specs fake a camera with Chromium launch flags and the `camera` permission. Firefox rejects the
      // permission ("Unknown permission: camera") and WebKit the flags (`Unknown option --use-fake-ui-for-media-stream`),
      // so they run in the Chromium engine only — Chromium itself and Edge, which is Chromium.
      const chromiumEngine = browser === 'chromium' || browser === 'edge';
      const fakeCameraSpecs = /recognize-sample(\.ai)?\.spec\.ts/;

      // Anonymous project
      projects.push({
        name: browser,
        grepInvert: /@auth/,
        testIgnore: chromiumEngine ? [] : [fakeCameraSpecs],
        use: { ...device },
      });

      // This browser's own sign-in: it registers an account and keeps the session its @auth project starts from.
      projects.push({
        name: `setup-${browser}`,
        testMatch: /auth\.setup\.ts/,
        use: { ...device },
      });

      // Authenticated project
      projects.push({
        name: `${browser}-auth`,
        grep: /@auth/,
        dependencies: [`setup-${browser}`],
        use: { ...device, storageState: authFilesFor(`${browser}-auth`).storageState },
      });
    }

    return projects;
  })(),
  testIgnore: [],
});
