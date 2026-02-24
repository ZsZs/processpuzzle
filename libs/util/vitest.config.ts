import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import { nxViteTsPaths } from '@nx/vite/plugins/nx-tsconfig-paths.plugin';
import path from 'path';

export default defineConfig({
  plugins: [
    angular(),
    nxViteTsPaths(), // 💡 Essential for resolving your "ProcessPuzzle" path aliases
  ],
  test: {
    globals: true,
    environment: 'jsdom',
    pool: 'threads',
    maxWorkers: 1,
    fileParallelism: false,
    setupFiles: [path.resolve(__dirname, './src/test-setup.ts')],
    include: ['./src/**/*.spec.ts'],
    reporters: ['default'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      reportsDirectory: './reports/coverage',
    },
    server: {
      deps: {
        inline: [/src\/test-setup\.ts/],
      },
    },
  },
});
