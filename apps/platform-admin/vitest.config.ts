import { defineConfig } from 'vitest/config';
import { join } from 'node:path';

export default defineConfig({
  root: __dirname,
  test: {
    coverage: {
      enabled: true,
      reportsDirectory: join(__dirname, 'reports/coverage'),
      reporter: ['lcov', 'html', 'text'],
      cleanOnRerun: true,
      // Negated glob names THIS directory: without it every other application's files leak into
      // the report. See [[vitest-coverage-scoping]].
      exclude: ['apps/!(platform-admin)/**'],
    },
  },
});
