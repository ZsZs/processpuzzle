#!/usr/bin/env node
// Per-lib Java release orchestration.
//
// Subcommand:
//   prepare --project <nx-project> --increment <major|minor|patch>
//
// Flow (matches tools/scripts/release-js-lib.mjs):
//   1. Assert on develop, clean tree
//   2. Read current version from root pom's <${project}.version> property
//   3. Compute new version via semver bump
//   4. Create release/<project>/<newVersion> branch
//   5. Bump the property via versions-maven-plugin (single edit, root pom only)
//   6. Commit, push, open PR to develop
//
// Design (why there's no finalize step):
//   The property doubles as (a) the lib's own artifact version and (b) the pin
//   siblings use in <dependencyManagement>. If it drifts to a -SNAPSHOT between
//   releases, every sibling depending on this lib becomes unreleasable because
//   the SNAPSHOT isn't on Central. So we keep the property at the LAST PUBLISHED
//   version between releases — no ceremony, next release just bumps.
//
// Preconditions:
//   - Root pom has a version property named <${project}.version> holding a plain
//     X.Y.Z semver (no -SNAPSHOT, no prereleases).
//   - The corresponding child pom's <version> uses ${<project>.version}.
//   - flatten-maven-plugin resolves the property at publish time.

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { parseArgs } from 'node:util';

const MVN = process.env.MVN ?? 'mvn';
const GH = process.env.GH ?? 'gh';

const PROJECTS = new Map([
  ['api-contracts', 'libs/java-shared/api-contracts'],
  ['base-desktop-backend', 'libs/java-shared/base-desktop-backend'],
  ['base-rule-backend', 'libs/java-shared/base-rule-backend'],
  ['base-state-backend', 'libs/java-shared/base-state-backend'],
  ['base-workflow-backend', 'libs/java-shared/base-workflow-backend'],
  ['processpuzzle-core', 'libs/java-shared/processpuzzle-core'],
  ['processpuzzle-store', 'libs/java-shared/processpuzzle-store'],
]);

const INCREMENTS = new Set(['major', 'minor', 'patch']);

// See [[node-spawn-cmd-windows]] memory: .cmd/.bat on Windows need shell:true
// (CVE-2024-27980) but shell:true mangles args for .exe callees. Predicate per callee.
const IS_WINDOWS = process.platform === 'win32';
function needsShell(cmd) {
  return IS_WINDOWS && (cmd === MVN || /\.(cmd|bat)$/i.test(cmd));
}

function run(cmd, args) {
  console.log(`$ ${cmd} ${args.join(' ')}`);
  execFileSync(cmd, args, { shell: needsShell(cmd), stdio: 'inherit' });
}

function capture(cmd, args) {
  return execFileSync(cmd, args, { shell: needsShell(cmd), encoding: 'utf8' }).trim();
}

function readCurrentVersion(project) {
  const pom = readFileSync('pom.xml', 'utf8');
  const re = new RegExp(`<${project}\\.version>([^<]+)</${project}\\.version>`);
  const m = pom.match(re);
  if (!m) {
    throw new Error(`Property <${project}.version> not found in root pom.xml`);
  }
  return m[1];
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

function setProperty(project, newVersion) {
  run(MVN, [
    '-N',
    'versions:set-property',
    `-Dproperty=${project}.version`,
    `-DnewVersion=${newVersion}`,
    '-DgenerateBackupPoms=false',
    '-q',
  ]);
}

function bumpVersion(current, increment) {
  const m = current.match(/^(\d+)\.(\d+)\.(\d+)$/);
  if (!m) {
    throw new Error(
      `Cannot bump version '${current}': only plain X.Y.Z semver is supported. ` +
        `If the property still has a -SNAPSHOT suffix from the old flow, strip it ` +
        `manually on develop first (set it to the last published version).`,
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
      `Unknown Java project '${project}'. Known: ${[...PROJECTS.keys()].join(', ')}`,
    );
  }
  if (!existsSync(`${path}/pom.xml`)) {
    throw new Error(`Expected ${path}/pom.xml to exist`);
  }
  return path;
}

function prepare(project, increment) {
  projectPath(project);
  assertOnBranch('develop');
  assertCleanTree();

  const current = readCurrentVersion(project);
  const newVersion = bumpVersion(current, increment);
  const branch = `release/${project}/${newVersion}`;

  console.log(`\n>>> Releasing ${project}: ${current} -> ${newVersion} (${increment})\n`);

  run('git', ['checkout', '-b', branch]);
  setProperty(project, newVersion);
  run('git', ['add', 'pom.xml']);
  run('git', ['commit', '-m', `release(${project}): bump version.`]);
  run('git', ['push', '-u', 'origin', branch]);
  run(GH, [
    'pr', 'create',
    '--base', 'develop',
    '--head', branch,
    '--title', `release(${project}): ${newVersion}`,
    '--body',
    `Release **${project}** at **${newVersion}** (${increment} bump).\n\n` +
      `The Maven Central deploy workflow triggers on push to \`${branch}\`. ` +
      `On publish success the release-merge composite auto-merges this PR ` +
      `into develop and opens+merges a PR into master.`,
  ]);

  console.log(`\nBranch pushed, PR opened. Watch the release workflow.`);
}

function usage() {
  return (
    `Usage:\n` +
    `  node tools/scripts/release-java-lib.mjs prepare --project <name> --increment <major|minor|patch>\n\n` +
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
