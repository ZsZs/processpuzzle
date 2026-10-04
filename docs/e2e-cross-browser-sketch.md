# Cross-browser E2E sketch (Playwright + Nx + GitHub Actions)

## playwright.config.ts

```ts
import { defineConfig, devices } from '@playwright/test';

const FULL = !!process.env.E2E_FULL; // nightly / pre-release
const smoke = FULL ? undefined : /@smoke/;

export default defineConfig({
  fullyParallel: true,
  workers: process.env.CI ? 4 : undefined,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['blob'], ['github']] : 'list',
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } }, // always full
    { name: 'firefox', use: { ...devices['Desktop Firefox'] }, grep: smoke },
    { name: 'webkit', use: { ...devices['Desktop Safari'] }, grep: smoke },
    // nightly only:
    ...(FULL
      ? [{ name: 'edge', use: { ...devices['Desktop Edge'], channel: 'msedge' } }]
      : []),
  ],
});
```

Tag critical flows:

```ts
test('login and open entity list @smoke', async ({ page }) => { /* ... */ });
```

## .github/workflows/e2e.yml

```yaml
name: e2e
on:
  pull_request:
  schedule:
    - cron: '0 2 * * *'   # nightly full run

jobs:
  e2e:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        browser: [chromium, firefox, webkit]
        shard: [1, 2, 3, 4]
    env:
      E2E_FULL: ${{ github.event_name == 'schedule' && '1' || '' }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 22, cache: npm }
      - run: npm ci
      - uses: actions/cache@v4
        with:
          path: ~/.cache/ms-playwright
          key: pw-${{ runner.os }}-${{ hashFiles('package-lock.json') }}
      - run: npx playwright install --with-deps ${{ matrix.browser }}
      - run: >
          npx nx e2e <app>-e2e --
          --project=${{ matrix.browser }}
          --shard=${{ matrix.shard }}/4
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: report-${{ matrix.browser }}-${{ matrix.shard }}
          path: blob-report
```

Notes:
- On PRs, Firefox/WebKit shards only run `@smoke` tests, so most finish in a minute or two. Chromium runs the full suite split 4 ways (~4 min).
- If Firefox/WebKit shards are nearly empty on PRs, drop their shard count to 1 with an `exclude`/`include` matrix.
- Merge blob reports in a final job (`npx playwright merge-reports`) for one HTML report.
- Replace `<app>-e2e` with your Nx e2e project name.
