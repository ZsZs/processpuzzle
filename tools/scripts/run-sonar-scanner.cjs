#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const projectPath = process.argv[2];
if (!projectPath) {
  console.error('Usage: run-sonar-scanner.cjs <project-path> [extra sonar-scanner args]');
  process.exit(1);
}

const absProjectPath = path.resolve(projectPath);
const propsFile = path.join(absProjectPath, 'sonar-project.properties');
if (!fs.existsSync(propsFile)) {
  console.error(`run-sonar-scanner: ${propsFile} not found`);
  process.exit(1);
}

if (!process.env.SONAR_TOKEN) {
  console.error('run-sonar-scanner: SONAR_TOKEN env var is not set');
  process.exit(1);
}

const lcov = path.join(absProjectPath, 'reports', 'coverage', 'lcov.info');
if (!fs.existsSync(lcov)) {
  console.warn(`run-sonar-scanner: ${lcov} not found — coverage will be 0%. Run the project's test target with --coverage first.`);
}

const props = fs.readFileSync(propsFile, 'utf8');
const hasHostUrl = /^\s*sonar\.host\.url\s*=/m.test(props);

const args = [`-Dsonar.token=${process.env.SONAR_TOKEN}`];
if (!hasHostUrl) args.push('-Dsonar.host.url=https://sonarcloud.io');
args.push(...process.argv.slice(3));

console.log(`run-sonar-scanner: running sonar-scanner in ${absProjectPath}`);
const result = spawnSync('sonar-scanner', args, {
  cwd: absProjectPath,
  stdio: 'inherit',
  shell: true,
});

if (result.error) {
  console.error(`run-sonar-scanner: failed to spawn sonar-scanner (${result.error.message}). Is it on PATH?`);
  process.exit(1);
}
process.exit(result.status ?? 1);
