import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';

export default defineConfig({
  plugins: [angular()],
  test: {
    globals: true, // This allows 'describe', 'it', 'expect' without imports
    environment: 'jsdom',
    include: ['**/*.spec.ts'],
    // This solves the Keycloak/ESM nightmare automatically:
    server: {
      deps: {
        inline: ['@angular/core', 'keycloak-js'],
      },
    },
  },
});
