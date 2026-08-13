import { defineConfig } from 'vitest/config';
import { join } from 'node:path';

export default defineConfig({
  root: __dirname,
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts'],
    // These specs exercise the express apps over real HTTP on an ephemeral port, and v8 coverage
    // instrumentation makes each request several times slower. The default 5 s is close enough to
    // that cost that whichever spec file happens to be scheduled last intermittently times out —
    // a scheduling artefact, not a slow assertion, so the deadline is raised rather than the
    // suites split.
    testTimeout: 30_000,
    hookTimeout: 30_000,
    coverage: {
      enabled: true,
      provider: 'v8',
      reportsDirectory: join(__dirname, 'reports/coverage'),
      reporter: ['lcov', 'html', 'text'],
      cleanOnRerun: true,
      include: ['src/**/*.ts'],
      // `*.model.ts` holds interfaces and type aliases only, so it compiles to no statements at all
      // and would otherwise be reported as 0% covered forever.
      exclude: ['src/**/*.spec.ts', 'src/**/test-support.ts', 'src/**/*.model.ts'],
      thresholds: { statements: 80, branches: 80, functions: 80, lines: 80 },
    },
  },
});
