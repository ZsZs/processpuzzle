import nx from '@nx/eslint-plugin';
import sonarjs from 'eslint-plugin-sonarjs';

/**
 * The analyzer family SonarCloud runs on a pull request, for the pre-push hook (tools/scripts/pre-push-check.sh).
 * Deliberately not part of eslint.config.mjs: `nx lint` covers whole projects, and the existing code carries
 * findings this config would turn into build failures. The hook applies it to the files a branch changes only.
 */
export default [
  ...nx.configs['flat/base'],
  ...nx.configs['flat/typescript'],
  { ...sonarjs.configs.recommended, files: ['**/*.ts', '**/*.tsx', '**/*.js', '**/*.jsx', '**/*.mjs', '**/*.cjs'] },
  { files: ['**/*.ts', '**/*.tsx'], rules: { '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' }] } },
  { linterOptions: { reportUnusedDisableDirectives: 'off' } },
  // The testbed's e2e specs declare suites (defineEntityCrudSuite and the like); the tests are generated, so a spec file
  // that only declares one is not an empty test file.
  { files: ['apps/processpuzzle-testbed-e2e/src/tests/**/*.spec.ts'], rules: { 'sonarjs/no-empty-test-file': 'off' } },
  // The generic e2e suites define tests whose assertions live in the page objects they drive (assertFieldValues,
  // assertNotInList and the like), which these rules cannot see into, and skip a test on purpose with the application's
  // own reason so the gap stays in the report. Set here rather than with disable comments in the suites: only this config
  // knows the sonarjs rules, so `nx lint` reports a comment naming one as an error.
  { files: ['libs/js-shared/e2e-testing/src/lib/suites/**/*.ts'], rules: { 'sonarjs/assertions-in-tests': 'off', 'sonarjs/no-skipped-tests': 'off' } },
  { ignores: ['**/dist', '**/reports', '**/coverage', 'tools/mock-backend'] },
];
