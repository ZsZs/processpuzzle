import { defineConfig } from 'vitest/config';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const projectRoot = dirname(fileURLToPath(import.meta.url));

const projectPrefix = 'libs/js-shared/util';

export default defineConfig({
  test: {
    coverage: {
      reportsDirectory: resolve(projectRoot, 'reports/coverage'),
      include: [`${projectPrefix}/src/**/*.ts`],
      exclude: [
        `${projectPrefix}/src/public-api.ts`,
        `${projectPrefix}/src/test-setup.ts`,
        `${projectPrefix}/src/**/*.module.ts`,
        `${projectPrefix}/src/**/*.spec.ts`,
      ],
    },
  },
});
