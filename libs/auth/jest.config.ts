export default {
  displayName: '@processpuzzle/auth',
  preset: '../../jest.preset.cjs',
  setupFilesAfterEnv: ['<rootDir>/test-setup.ts'],
  coverageDirectory: 'reports/coverage',
  coverageReporters: [
    ['lcov', { projectRoot: 'libs/auth' }],
    ['text', { skipFull: true }],
  ],
  collectCoverageFrom: ['domain/**/*.ts', 'feature/**/*.ts', '!**/main.ts', '!**/public-api.ts', '!**/*.provider.ts', '!**/*.module.ts', '!**/*.spec.ts', '!environments/**/*.ts'],
  testEnvironment: '@happy-dom/jest-environment',
  transform: {
    '^.+\\.(ts|mjs|js|html)$': ['jest-preset-angular', { tsconfig: '<rootDir>/tsconfig.spec.json', stringifyContentPathRegex: String.raw`\.(html|svg)$` }],
  },
  transformIgnorePatterns: [String.raw`node_modules/(?!(.*\.mjs$|@jsverse/.*|@angular/.*|rxjs/.*|tslib/.*|uuid/.*))`],
  snapshotSerializers: ['jest-preset-angular/build/serializers/no-ng-attributes', 'jest-preset-angular/build/serializers/ng-snapshot', 'jest-preset-angular/build/serializers/html-comment'],
  moduleDirectories: ['node_modules'],
};
