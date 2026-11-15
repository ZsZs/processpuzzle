import nx from '@nx/eslint-plugin';
import baseConfig from '../../../eslint.config.mjs';

export default [
  ...baseConfig,
  ...nx.configs['flat/angular'],
  ...nx.configs['flat/angular-template'],
  {
    files: ['**/*.ts'],
    rules: {
      '@angular-eslint/directive-selector': ['error', { type: 'attribute', prefix: 'pp', style: 'camelCase' }],
      '@angular-eslint/component-selector': ['error', { type: 'element', prefix: 'pp', style: 'kebab-case' }],
    },
  },
  {
    files: ['**/*.html'],
    rules: {},
  },
  {
    // Layers: `feature` builds on `domain`, never the other way round. The routes and providers files sit
    // above both, so the domain may not reach them either. (The backend's counterpart is
    // base-chat-backend's HexagonalLayerRules.)
    files: ['src/lib/domain/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/feature', '**/feature/**', '**/base-chat.routes', '**/base-chat.providers'],
              message: 'The domain layer must not depend on the feature layer, the routes or the providers.',
            },
          ],
        },
      ],
    },
  },
];
