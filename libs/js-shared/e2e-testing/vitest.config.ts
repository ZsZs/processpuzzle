import { coverageConfigDefaults, defineConfig } from 'vitest/config';
import { join } from 'node:path';

/**
 * Unit tests for the parts of this library that are not a browser.
 *
 * Most of what lives here drives Playwright: a page object's correctness is whether its selectors match the DOM
 * the application actually renders, and the only thing that can answer that is a real run against a real app —
 * which is what the generated suites do in CI. A unit test asserting that a page object called
 * `getByRole('button', {name: 'Upload'})` on a mock passes just as happily when the button says something else,
 * so those files are excluded from coverage rather than covered by tests that cannot fail for the right reason.
 *
 * What remains is genuinely unit-testable: descriptor interpretation, route and selector construction, generated
 * test data, and the binary fixtures. Those are where a failing assertion means something.
 */
export default defineConfig({
  root: __dirname,
  test: {
    environment: 'node',
    coverage: {
      enabled: true,
      provider: 'v8',
      reportsDirectory: join(__dirname, 'reports/coverage'),
      reporter: ['lcov', 'html', 'text'],
      cleanOnRerun: true,
      exclude: [
        ...coverageConfigDefaults.exclude,
        'src/public-api.ts',
        // Browser-driving: verified by the generated suites running green against a real application.
        'src/lib/pages/**',
        'src/lib/suites/**',
        'src/lib/setup/**',
        'src/lib/data/entity-crud-fixture-manager.ts',
      ],
    },
  },
});
