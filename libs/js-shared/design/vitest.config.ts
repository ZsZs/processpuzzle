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
      exclude: ['libs/js-shared/!(design)/**'],
    },
  },
});
