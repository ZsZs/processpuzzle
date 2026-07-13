#!/usr/bin/env node
// Per-lib JS/TS release orchestration.
//
// Subcommand:
//   prepare --project <nx-project> --increment <major|minor|patch>
//
// Flow (mirrors tools/scripts/release-java-lib.mjs):
//   1. Assert on develop, clean tree
//   2. Read current version from libs/js-shared/<lib>/package.json
//   3. Compute new version via semver bump
//   4. Safety check: nx run <lib>:build --configuration=production
//   5. Create release/<lib>/<newVersion> branch
//   6. Rewrite package.json version, commit, push, open PR
//
// No finalize step — JS libs don't use SNAPSHOTs; the release commit is the bump.

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { parseArgs } from 'node:util';

const NPM = process.env.NPM ?? 'npm';
const NPX = process.env.NPX ?? 'npx';
const GH = process.env.GH ?? 'gh';

const PROJECTS = new Map([
  ['auth', 'libs/js-shared/auth'],
  ['base-desktop-frontend', 'libs/js-shared/base-desktop-frontend'],
  ['base-entity-frontend', 'libs/js-shared/base-entity-frontend'],
  ['base-rule-frontend', 'libs/js-shared/base-rule-frontend'],
  ['base-state-frontend', 'libs/js-shared/base-state-frontend'],
  ['base-workflow-frontend', 'libs/js-shared/base-workflow-frontend'],
  ['design', 'libs/js-shared/design'],
  ['e2e-testing', 'libs/js-shared/e2e-testing'],
  ['test-util', 'libs/js-shared/test-util'],
  ['util', 'libs/js-shared/util'],
  ['widgets', 'libs/js-shared/widgets'],
]);

const INCREMENTS = new Set(['major', 'minor', 'patch']);

// See [[node-spawn-cmd-windows]] memory: .cmd/.bat files on Windows need shell:true
// (CVE-2024-27980), but that mangles args for .exe callees. Predicate per callee.
const IS_WINDOWS = process.platform === 'win32';
function needsShell(cmd) {
  return IS_WINDOWS && (cmd === NPM || cmd === NPX || /\.(cmd|bat)$/i.test(cmd));
}

function run(cmd, args) {
  console.log(`$ ${cmd} ${args.join(' ')}`);
  execFileSync(cmd, args, { shell: needsShell(cmd), stdio: 'inherit' });
}

function capture(cmd, args) {
  return execFileSync(cmd, args, { shell: needsShell(cmd), encoding: 'utf8' }).trim();
}

function assertCleanTree() {
  const status = capture('git', ['status', '--porcelain']);
  if (status) {
    throw new Error(`Working tree is not clean:\n${status}`);
  }
}

function assertOnBranch(branch) {
  const current = capture('git', ['branch', '--show-current']);
  if (current !== branch) {
    throw new Error(`Expected to be on branch '${branch}', but on '${current}'`);
  }
}

function readPackageJson(path) {
  return JSON.parse(readFileSync(`${path}/package.json`, 'utf8'));
}

function writePackageJson(path, pkg) {
  writeFileSync(`${path}/package.json`, JSON.stringify(pkg, null, 2) + '\n');
}

function bumpVersion(current, increment) {
  const m = current.match(/^(\d+)\.(\d+)\.(\d+)$/);
  if (!m) {
    throw new Error(
      `Cannot bump version '${current}': only plain X.Y.Z semver is supported. ` +
        `Bump the version manually if you need a prerelease/build tag.`,
    );
  }
  const major = Number(m[1]);
  const minor = Number(m[2]);
  const patch = Number(m[3]);
  switch (increment) {
    case 'major': return `${major + 1}.0.0`;
    case 'minor': return `${major}.${minor + 1}.0`;
    case 'patch': return `${major}.${minor}.${patch + 1}`;
    default: throw new Error(`Unknown increment '${increment}' (expected major|minor|patch)`);
  }
}

function projectPath(project) {
  const path = PROJECTS.get(project);
  if (!path) {
    throw new Error(
      `Unknown JS project '${project}'. Known: ${[...PROJECTS.keys()].join(', ')}`,
    );
  }
  if (!existsSync(`${path}/package.json`)) {
    throw new Error(`Expected ${path}/package.json to exist`);
  }
  return path;
}

function prepare(project, increment) {
  const path = projectPath(project);
  assertOnBranch('develop');
  assertCleanTree();

  const pkg = readPackageJson(path);
  const current = pkg.version;
  if (typeof current !== 'string') {
    throw new Error(`No "version" field in ${path}/package.json`);
  }
  const newVersion = bumpVersion(current, increment);
  const branch = `release/${project}/${newVersion}`;

  console.log(`\n>>> Releasing ${project}: ${current} -> ${newVersion} (${increment})\n`);

  console.log('Pre-release safety check: building the target lib in production config...');
  run(NPX, ['nx', 'run', `${project}:build`, '--configuration=production']);

  run('git', ['checkout', '-b', branch]);
  pkg.version = newVersion;
  writePackageJson(path, pkg);
  run('git', ['add', `${path}/package.json`]);
  run('git', ['commit', '-m', `release(${project}): bump version.`]);
  run('git', ['push', '-u', 'origin', branch]);
  run(GH, [
    'pr', 'create',
    '--base', 'develop',
    '--head', branch,
    '--title', `release(${project}): ${newVersion}`,
    '--body',
    `Release **${project}** at **${newVersion}** (${increment} bump).\n\n` +
      `The npm publish workflow triggers on push to \`${branch}\`. ` +
      `Merge this PR after publish succeeds.`,
  ]);

  console.log(`\nBranch pushed, PR opened. Watch the release workflow, then merge the PR.`);
}

function usage() {
  return (
    `Usage:\n` +
    `  node tools/scripts/release-js-lib.mjs prepare --project <name> --increment <major|minor|patch>\n\n` +
    `Known projects:\n  ${[...PROJECTS.keys()].join('\n  ')}\n`
  );
}

function main() {
  const [subcommand, ...rest] = process.argv.slice(2);
  if (!subcommand || subcommand === '--help' || subcommand === '-h') {
    console.log(usage());
    process.exit(subcommand ? 0 : 2);
  }

  let values;
  try {
    ({ values } = parseArgs({
      args: rest,
      options: {
        project: { type: 'string', short: 'p' },
        increment: { type: 'string', short: 'i' },
      },
    }));
  } catch (err) {
    console.error(err.message);
    console.error(usage());
    process.exit(2);
  }

  if (!values.project) {
    console.error('Missing --project <name>');
    console.error(usage());
    process.exit(2);
  }
  if (!values.increment) {
    console.error('Missing --increment <major|minor|patch>');
    console.error(usage());
    process.exit(2);
  }
  if (!INCREMENTS.has(values.increment)) {
    console.error(`Invalid --increment '${values.increment}' (expected major|minor|patch)`);
    console.error(usage());
    process.exit(2);
  }

  if (subcommand !== 'prepare') {
    console.error(`Unknown subcommand: ${subcommand}`);
    console.error(usage());
    process.exit(2);
  }

  prepare(values.project, values.increment);
}

try {
  main();
} catch (err) {
  console.error(`\nERROR: ${err.message}`);
  process.exit(1);
}
