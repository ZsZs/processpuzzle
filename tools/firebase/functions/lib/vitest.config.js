"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const config_1 = require("vitest/config");
const node_path_1 = require("node:path");
exports.default = (0, config_1.defineConfig)({
    root: __dirname,
    test: {
        environment: 'node',
        include: ['src/**/*.spec.ts'],
        coverage: {
            enabled: true,
            provider: 'v8',
            reportsDirectory: (0, node_path_1.join)(__dirname, 'reports/coverage'),
            reporter: ['lcov', 'html', 'text'],
            cleanOnRerun: true,
            include: ['src/**/*.ts'],
            exclude: ['src/**/*.spec.ts', 'src/**/test-support.ts'],
            thresholds: { statements: 80, branches: 80, functions: 80, lines: 80 },
        },
    },
});
//# sourceMappingURL=vitest.config.js.map