import * as path from 'node:path';

const projectRoot = path.resolve(__dirname, '../../');

export default {
  displayName: '@processpuzzle/auth',
  preset: '../../jest.preset.js',
  setupFilesAfterEnv: ['<rootDir>/src/test-setup.ts'],
  coverageDirectory: 'reports/coverage',
  coverageReporters: [
    ['lcov', { projectRoot: 'libs/auth' }],
    ['text', { skipFull: true }],
  ],
  collectCoverageFrom: ['src/**/*.ts', '!src/main.ts', '!src/public-api.ts', '!src/**/*.module.ts', '!src/**/*.spec.ts', '!src/environments/**/*.ts'],
  testEnvironment: '@happy-dom/jest-environment',
  transform: {
    '^.+\\.(ts|mjs|js|html)$': ['jest-preset-angular', { tsconfig: '<rootDir>/tsconfig.spec.json', stringifyContentPathRegex: '\\.(html|svg)$' }],
  },
  transformIgnorePatterns: ['node_modules/(?!.*\\.mjs$)'],
  snapshotSerializers: ['jest-preset-angular/build/serializers/no-ng-attributes', 'jest-preset-angular/build/serializers/ng-snapshot', 'jest-preset-angular/build/serializers/html-comment'],
  moduleDirectories: ['node_modules'],
  resolver: path.join(projectRoot, 'node_modules/@nx/jest/plugins/resolver.js'),
};
