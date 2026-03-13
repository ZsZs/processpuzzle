import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import { nxViteTsPaths } from '@nx/vite/plugins/nx-tsconfig-paths.plugin';

export default defineConfig({
  plugins: [
    angular({
      jit: true,
    }),
    nxViteTsPaths(), // 💡 Essential for resolving your "ProcessPuzzle" path aliases
  ],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['test-setup.ts'],
    include: ['src/**/*.spec.ts'],
    reporters: ['default'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      reportsDirectory: './reports/coverage',
    },
    server: {
      deps: {
        inline: [
          '@angular/core',
          '@angular/common',
          '@angular/compiler',
          '@angular/fire',
          '@angular/platform-browser',
          '@angular/platform-browser-dynamic',
          '@angular/router',
          '@angular/cdk',
          '@angular/material',
          '@testing-library/angular',
          '@jsverse/transloco',
          'rxfire',
        ],
        external: ['firebase', 'firebase-admin'],
      },
    },
  },
  resolve: {
    conditions: ['default'],
  },
});
