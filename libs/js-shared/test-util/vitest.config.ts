import { defineConfig, mergeConfig } from 'vitest/config';
import { join } from 'path';
// eslint-disable-next-line @nx/enforce-module-boundaries
import baseConfig from '../../../vitest.config';

export default mergeConfig(
  baseConfig,
  defineConfig({
    test: {
      coverage: {
        reportsDirectory: join(__dirname, 'reports', 'coverage'),
        reporter: ['text', 'lcov', 'html'],
      },
    },
  }),
);
