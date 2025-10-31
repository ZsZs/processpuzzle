export default {
  displayName: '@processpuzzle/widgets',
  preset: '../../jest.preset.cjs',
  setupFilesAfterEnv: ['<rootDir>/test-setup.ts'],
  coverageDirectory: 'reports/coverage',
  coverageReporters: [
    ['lcov', { projectRoot: 'libs/widgets' }],
    ['text', { skipFull: true }],
  ],
  collectCoverageFrom: ['**/*.ts', '!jest.config.ts', '!**/public-api.ts', '!**/*.routes.ts', '!**/*.provider.ts', '!**/*.module.ts', '!**/*.spec.ts', '!**/environments/**/*.ts'],
  testEnvironment: '@happy-dom/jest-environment',
  transform: {
    '^.+\\.(ts|mjs|js|html)$': [
      'jest-preset-angular',
      {
        tsconfig: '<rootDir>/tsconfig.spec.json',
        stringifyContentPathRegex: String.raw`\.(html|svg)$`,
      },
    ],
  },
  transformIgnorePatterns: [String.raw`node_modules/(?!(.*\.mjs$|@jsverse/.*|@angular/.*|rxjs/.*|tslib/.*|uuid/.*))`],
  snapshotSerializers: ['jest-preset-angular/build/serializers/no-ng-attributes', 'jest-preset-angular/build/serializers/ng-snapshot', 'jest-preset-angular/build/serializers/html-comment'],
};
