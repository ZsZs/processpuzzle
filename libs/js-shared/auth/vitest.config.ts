import { configDefaults, defineConfig } from 'vitest/config';
import { join } from 'node:path';

export default defineConfig({
  root: __dirname,
  test: {
    exclude: [
      ...configDefaults.exclude,
      '**/domain/service/firebase-auth.service.spec.ts',
      '**/domain/service/provide-firebase-auth-service.spec.ts',
    ],
    coverage: {
      enabled: true,
      reportsDirectory: join(__dirname, 'reports/coverage'),
      reporter: ['lcov', 'html', 'text'],
      cleanOnRerun: true,
      exclude: [
        'libs/js-shared/!(auth)/**',
        'libs/js-shared/auth/feature/test-form/**',
        'libs/js-shared/auth/domain/service/firebase-auth.service.ts',
        'libs/js-shared/auth/domain/service/provide-firebase-auth-service.ts',
      ],
    },
  },
});
