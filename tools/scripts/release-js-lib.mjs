#!/usr/bin/env node
// Per-lib JS/TS release orchestration.
//
// Subcommands:
//   prepare --project <nx-project> --increment <major|minor|patch> [--dry-run]
//   order
//
// --dry-run keeps every check (branch, clean tree, production build) and prints the version
// bump plus the dependents it would retarget, but writes nothing and runs no git/gh command.
//
// prepare flow (mirrors tools/scripts/release-java-lib.mjs):
//   1. Assert on develop, clean tree
//   2. Read current version from libs/js-shared/<lib>/package.json
//   3. Compute new version via semver bump
//   4. Safety check: nx run <lib>:build --configuration=production
//   5. Create release/<lib>/<newVersion> branch
//   6. Rewrite package.json version AND every dependent's range on this package,
//      commit, push, open PR
//
// order prints the release tiers derived from the @processpuzzle/* peer dependencies.
//
// Why step 6 rewrites dependents too: Nx resolves the libs through tsconfig paths, so an
// out-of-range peer range is invisible in-repo and only bites a consumer installing from
// npm. Bumping @processpuzzle/base-entity to 1.x while nine dependents pin ^0.8.x produced
// exactly that (a caret on 0.x admits only 0.8.y), so the bump now carries the ranges with
// it. The dependents' own versions are NOT bumped — the widened requirement ships with each
// dependent's next release.
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
  ['base-app-frontend', 'libs/js-shared/base-app-frontend'],
  ['base-document-frontend', 'libs/js-shared/base-document-frontend'],
  ['base-entity-frontend', 'libs/js-shared/base-entity-frontend'],
  ['base-rule-frontend', 'libs/js-shared/base-rule-frontend'],
  ['base-state-frontend', 'libs/js-shared/base-state-frontend'],
  ['base-workflow-frontend', 'libs/js-shared/base-workflow-frontend'],
  ['design', 'libs/js-shared/design'],
  ['org-admin-frontend', 'libs/js-shared/org-admin-frontend'],
  ['e2e-testing', 'libs/js-shared/e2e-testing'],
  ['test-util', 'libs/js-shared/test-util'],
  ['util', 'libs/js-shared/util'],
  ['base-widget-frontend', 'libs/js-shared/base-widget-frontend'],
  ['processpuzzle-testbed-frontend', 'apps/processpuzzle-testbed-frontend'],
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

// A dry run writes nothing, so a failing precondition is reported rather than fatal — that
// way the checks below can be exercised from a tree that still holds work in progress.
function assertOrWarn(dryRun, message) {
  if (!dryRun) throw new Error(message);
  console.warn(`[dry-run] WOULD FAIL: ${message}`);
}

function assertCleanTree(dryRun = false) {
  const status = capture('git', ['status', '--porcelain']);
  if (status) {
    assertOrWarn(dryRun, `Working tree is not clean:\n${status}`);
  }
}

function assertOnBranch(branch, dryRun = false) {
  const current = capture('git', ['branch', '--show-current']);
  if (current !== branch) {
    assertOrWarn(dryRun, `Expected to be on branch '${branch}', but on '${current}'`);
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

// The dependency ranges live under these two keys. Libraries use peerDependencies (Angular
// packages, so the consumer owns the single copy); dependencies is covered too, in case a
// manifest ever declares one there.
const RANGE_KEYS = ['dependencies', 'peerDependencies'];

// Rewrites every dependent's range on `packageName` to ^newVersion. Returns the paths of the
// manifests it changed, so the caller can `git add` them. With dryRun the rewrites are only
// reported, never written.
function retargetDependents(packageName, newVersion, selfPath, dryRun = false) {
  const changed = [];
  for (const [dependent, path] of PROJECTS) {
    if (path === selfPath) continue;
    const pkg = readPackageJson(path);
    let touched = false;
    for (const key of RANGE_KEYS) {
      const current = pkg[key]?.[packageName];
      if (current === undefined || current === `^${newVersion}`) continue;
      console.log(`  ${dependent}: ${key}.${packageName} ${current} -> ^${newVersion}`);
      pkg[key][packageName] = `^${newVersion}`;
      touched = true;
    }
    if (touched) {
      if (!dryRun) writePackageJson(path, pkg);
      changed.push(`${path}/package.json`);
    }
  }
  return changed;
}

// project -> { path, name, version, deps: [npm package names of in-repo dependencies] }.
// Derived from the manifests rather than hardcoded: the npm names are not derivable from
// the project names (base-entity-frontend -> @processpuzzle/base-entity).
function buildJsGraph() {
  const graph = new Map();
  for (const [project, path] of PROJECTS) {
    const pkg = readPackageJson(path);
    const deps = new Set();
    for (const key of RANGE_KEYS) {
      for (const name of Object.keys(pkg[key] ?? {})) {
        if (name.startsWith('@processpuzzle/')) deps.add(name);
      }
    }
    graph.set(project, { path, name: pkg.name, version: pkg.version, deps: [...deps] });
  }
  const byName = new Map([...graph].map(([project, info]) => [info.name, project]));
  for (const info of graph.values()) {
    info.depProjects = info.deps.map((name) => byName.get(name)).filter(Boolean);
  }
  return graph;
}

// Kahn's algorithm, grouping by level so each tier can be released in parallel.
function releaseTiers(graph) {
  const remaining = new Map(
    [...graph].map(([project, info]) => [project, new Set(info.depProjects)]),
  );
  const tiers = [];
  while (remaining.size) {
    const ready = [...remaining].filter(([, deps]) => deps.size === 0).map(([p]) => p);
    if (!ready.length) {
      throw new Error(`Dependency cycle among: ${[...remaining.keys()].join(', ')}`);
    }
    tiers.push(ready);
    for (const project of ready) remaining.delete(project);
    for (const deps of remaining.values()) {
      for (const project of ready) deps.delete(project);
    }
  }
  return tiers;
}

function order() {
  const graph = buildJsGraph();
  const tiers = releaseTiers(graph);
  console.log(
    `JS release order — derived from the @processpuzzle/* ranges in each package.json.\n` +
      `Release a tier only once every lib in the earlier tiers is on npm.\n`,
  );
  for (const [i, tier] of tiers.entries()) {
    const entries = tier.map((project) => {
      const info = graph.get(project);
      return `${project} (${info.name}) ${info.version}`;
    });
    console.log(`  ${i + 1}. ${entries.join(', ')}`);
  }
}

function prepare(project, increment, dryRun = false) {
  const path = projectPath(project);
  assertOnBranch('develop', dryRun);
  assertCleanTree(dryRun);

  const pkg = readPackageJson(path);
  const current = pkg.version;
  if (typeof current !== 'string') {
    throw new Error(`No "version" field in ${path}/package.json`);
  }
  if (typeof pkg.name !== 'string') {
    throw new Error(`No "name" field in ${path}/package.json`);
  }
  const newVersion = bumpVersion(current, increment);
  const branch = `release/${project}/${newVersion}`;

  const tag = dryRun ? '[dry-run] ' : '';
  console.log(
    `\n>>> ${tag}Releasing ${project}: ${current} -> ${newVersion} (${increment})\n`,
  );

  console.log('Pre-release safety check: building the target lib in production config...');
  run(NPX, ['nx', 'run', `${project}:build`, '--configuration=production']);

  const prBody =
    `Release **${project}** at **${newVersion}** (${increment} bump).\n\n` +
    `The npm publish workflow triggers on push to \`${branch}\`. ` +
    `Merge this PR after publish succeeds.`;

  if (!dryRun) run('git', ['checkout', '-b', branch]);
  pkg.version = newVersion;
  if (!dryRun) writePackageJson(path, pkg);

  console.log(`Retargeting dependents' ranges on ${pkg.name}...`);
  const dependents = retargetDependents(pkg.name, newVersion, path, dryRun);
  if (!dependents.length) {
    console.log('  (none)');
  }

  // The target's own package.json is always in the commit, so the release workflows'
  // `paths:` filter still matches.
  const commands = [
    ['git', ['checkout', '-b', branch]],
    ['git', ['add', `${path}/package.json`, ...dependents]],
    ['git', ['commit', '-m', `release(${project}): bump version.`]],
    ['git', ['push', '-u', 'origin', branch]],
    [GH, ['pr', 'create',
      '--base', 'develop',
      '--head', branch,
      '--title', `release(${project}): ${newVersion}`,
      '--body', prBody,
    ]],
  ];

  if (dryRun) {
    console.log(`\n[dry-run] Nothing was written. Would have run:`);
    for (const [cmd, args] of commands) console.log(`  $ ${cmd} ${args.join(' ')}`);
    console.log(
      `\n[dry-run] ${project} ${current} -> ${newVersion} looks releasable ` +
        `(${dependents.length} dependent manifest(s) retargeted).`,
    );
    return;
  }

  for (const [cmd, args] of commands.slice(1)) run(cmd, args);

  console.log(`\nBranch pushed, PR opened. Watch the release workflow, then merge the PR.`);
}

function usage() {
  return (
    `Usage:\n` +
    `  node tools/scripts/release-js-lib.mjs prepare --project <name> --increment <major|minor|patch> [--dry-run]\n` +
    `  node tools/scripts/release-js-lib.mjs order\n\n` +
    `Known projects:\n  ${[...PROJECTS.keys()].join('\n  ')}\n`
  );
}

function main() {
  const [subcommand, ...rest] = process.argv.slice(2);
  if (!subcommand || subcommand === '--help' || subcommand === '-h') {
    console.log(usage());
    process.exit(subcommand ? 0 : 2);
  }

  if (subcommand === 'order') {
    order();
    return;
  }

  if (subcommand !== 'prepare') {
    console.error(`Unknown subcommand: ${subcommand}`);
    console.error(usage());
    process.exit(2);
  }

  let values;
  try {
    ({ values } = parseArgs({
      args: rest,
      options: {
        project: { type: 'string', short: 'p' },
        increment: { type: 'string', short: 'i' },
        'dry-run': { type: 'boolean', short: 'n' },
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

  prepare(values.project, values.increment, values['dry-run'] ?? false);
}

try {
  main();
} catch (err) {
  console.error(`\nERROR: ${err.message}`);
  process.exit(1);
}
