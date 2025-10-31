export default {
  displayName: 'test-util',
  preset: '../../jest.preset.cjs',
  setupFilesAfterEnv: ['<rootDir>/src/test-setup.ts'],
  coverageDirectory: 'reports/coverage',
  coverageReporters: [
    ['lcov', { projectRoot: 'libs/test-util' }],
    ['text', { skipFull: true }],
  ],
  collectCoverageFrom: ['src/**/*.ts', '!src/main.ts', '!src/public-api.ts', '!src/**/*.provider.ts', '!src/**/*.module.ts', '!src/**/*.spec.ts', '!src/environments/**/*.ts'],
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
