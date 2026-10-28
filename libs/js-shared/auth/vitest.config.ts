import { configDefaults, defineConfig } from 'vitest/config';
import { join } from 'node:path';

export default defineConfig({
  root: __dirname,
  test: {
    exclude: [...configDefaults.exclude],
    coverage: {
      enabled: true,
      reportsDirectory: join(__dirname, 'reports/coverage'),
      reporter: ['lcov', 'html', 'text'],
      cleanOnRerun: true,
      exclude: [
        'libs/js-shared/!(auth)/**',
        'libs/js-shared/auth/feature/test-form/**',
      ],
    },
  },
});
