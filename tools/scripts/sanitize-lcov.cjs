#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const projectPath = process.argv[2];
if (!projectPath) {
  console.error('Usage: sanitize-lcov.cjs <project-path>');
  process.exit(1);
}

const lcovPath = path.join(projectPath, 'reports', 'coverage', 'lcov.info');
if (!fs.existsSync(lcovPath)) {
  console.warn(`sanitize-lcov: ${lcovPath} not found, skipping`);
  process.exit(0);
}

const posixPrefix = projectPath.replace(/\\/g, '/').replace(/\/$/, '') + '/';
const winPrefix = projectPath.replace(/\//g, '\\').replace(/\\$/, '') + '\\';

const sanitized = fs
  .readFileSync(lcovPath, 'utf8')
  .split(/\r?\n/)
  .map((line) => {
    if (!line.startsWith('SF:')) return line;
    let file = line.slice(3);
    if (file.startsWith(posixPrefix)) file = file.slice(posixPrefix.length);
    else if (file.startsWith(winPrefix)) file = file.slice(winPrefix.length);
    return 'SF:' + file.replace(/\\/g, '/');
  })
  .join('\n');

fs.writeFileSync(lcovPath, sanitized);
console.log(`sanitize-lcov: rewrote ${lcovPath}`);
