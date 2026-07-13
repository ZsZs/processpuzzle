#!/usr/bin/env node
// Per-lib Java release orchestration.
//
// Subcommands:
//   prepare  --project <nx-project>   create release branch, bump SNAPSHOT -> release
//                                     version, commit, push, open PR (GH Action deploys).
//   finalize --project <nx-project>   on develop, bump patch to next -SNAPSHOT, push.
//
// Preconditions (enforced by the pom refactor):
//   - Root pom has a version property named <${project}.version>
//   - The corresponding child pom's <version> element references that property
//     (e.g. <version>${processpuzzle-core.version}</version>)
//   - flatten-maven-plugin (already configured in the root pom) resolves the reference
//     at publish time so poms deployed to Central carry a literal version.
//
// Bumping is therefore a single edit to the root pom property.

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

function run(cmd, args) {
  console.log(`$ ${cmd} ${args.join(' ')}`);
  execFileSync(cmd, args, { stdio: 'inherit' });
}

function capture(cmd, args) {
  return execFileSync(cmd, args, { encoding: 'utf8' }).trim();
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

function bumpVersion(project, newVersion) {
  run(MVN, [
    '-N',
    'versions:set-property',
    `-Dproperty=${project}.version`,
    `-DnewVersion=${newVersion}`,
    '-DgenerateBackupPoms=false',
    '-q',
  ]);
}

function stripSnapshot(v) {
  if (!v.endsWith('-SNAPSHOT')) {
    throw new Error(`Version '${v}' does not end with -SNAPSHOT`);
  }
  return v.slice(0, -'-SNAPSHOT'.length);
}

function nextPatchSnapshot(releaseVersion) {
  const parts = releaseVersion.split('.');
  if (parts.length !== 3 || parts.some((p) => !/^\d+$/.test(p))) {
    throw new Error(`Cannot bump patch of non-semver version '${releaseVersion}'`);
  }
  const [major, minor, patch] = parts.map(Number);
  return `${major}.${minor}.${patch + 1}-SNAPSHOT`;
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

function prepare(project) {
  projectPath(project);
  assertOnBranch('develop');
  assertCleanTree();

  const currentSnapshot = readCurrentVersion(project);
  const releaseVersion = stripSnapshot(currentSnapshot);
  const branch = `release/${project}/${releaseVersion}`;

  console.log(`\n>>> Releasing ${project}: ${currentSnapshot} -> ${releaseVersion}\n`);

  run('git', ['checkout', '-b', branch]);
  bumpVersion(project, releaseVersion);
  run('git', ['add', 'pom.xml']);
  run('git', ['commit', '-m', `release(${project}): bump version.`]);
  run('git', ['push', '-u', 'origin', branch]);
  run(GH, [
    'pr', 'create',
    '--base', 'develop',
    '--head', branch,
    '--title', `release(${project}): ${releaseVersion}`,
    '--body',
    `Release **${project}** at **${releaseVersion}**.\n\n` +
      `The Maven Central deploy workflow triggers on push to \`${branch}\`. ` +
      `Once it succeeds and this PR is merged, run:\n\n` +
      `\`\`\`\nnx run ${project}:release-finalize\n\`\`\`\n\n` +
      `to bump develop to the next -SNAPSHOT.`,
  ]);

  console.log(`\nBranch pushed, PR opened. Watch the release workflow, then merge the PR.`);
  console.log(`After merge, on develop, run: nx run ${project}:release-finalize`);
}

function finalize(project) {
  projectPath(project);
  assertOnBranch('develop');
  assertCleanTree();

  const current = readCurrentVersion(project);
  if (current.endsWith('-SNAPSHOT')) {
    throw new Error(
      `Version '${current}' is already a SNAPSHOT. Nothing to finalize. ` +
        `Did the release PR get merged into develop?`,
    );
  }
  const next = nextPatchSnapshot(current);

  console.log(`\n>>> Bumping next SNAPSHOT for ${project}: ${current} -> ${next}\n`);

  bumpVersion(project, next);
  run('git', ['add', 'pom.xml']);
  run('git', ['commit', '-m', `chore(${project}): bump next SNAPSHOT version.`]);
  run('git', ['push', 'origin', 'develop']);

  console.log(`\nNext SNAPSHOT (${next}) committed and pushed on develop.`);
}

function usage() {
  return (
    `Usage:\n` +
    `  node tools/scripts/release-java-lib.mjs prepare  --project <name>\n` +
    `  node tools/scripts/release-java-lib.mjs finalize --project <name>\n\n` +
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

  switch (subcommand) {
    case 'prepare':
      prepare(values.project);
      break;
    case 'finalize':
      finalize(values.project);
      break;
    default:
      console.error(`Unknown subcommand: ${subcommand}`);
      console.error(usage());
      process.exit(2);
  }
}

try {
  main();
} catch (err) {
  console.error(`\nERROR: ${err.message}`);
  process.exit(1);
}
