import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { STORAGE_STATE } from './src/support/paths';

/**
 * Deliberately not `playwright.config.ts`: `@nx/playwright/plugin` infers an `e2e` target from that name, and a
 * recording is not a test run CI should pick up with the rest. The targets are in project.json.
 *
 * The containerised stack (`npm run stack-up-full`, :9090) is the default, because that is the topology every
 * environment runs; `TUTORIAL_BASE_URL` and `ENVIRONMENT` point it elsewhere.
 */
export const baseURL = process.env['TUTORIAL_BASE_URL'] ?? 'http://localhost:9090';

export default defineConfig({
  testDir: './src',
  outputDir: path.join(__dirname, 'tmp', 'test-results'),
  // One recording at a time: the scenarios create and remove the same definitions, in the same organization.
  workers: 1,
  fullyParallel: false,
  retries: 0,
  // Speech synthesis, a three-minute session and the encode, with room for a stack that has just started.
  timeout: 15 * 60_000,
  reporter: [['list']],
  use: { baseURL, ...devices['Desktop Chrome'] },
  projects: [
    { name: 'sign-in', testMatch: /sign-in\.setup\.ts/ },
    { name: 'record', testMatch: /record\.spec\.ts/, dependencies: ['sign-in'], use: { storageState: STORAGE_STATE } },
  ],
});
