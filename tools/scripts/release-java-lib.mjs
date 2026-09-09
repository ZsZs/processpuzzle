#!/usr/bin/env node
// Per-lib Java release orchestration.
//
// Subcommands:
//   prepare --project <name> --increment <major|minor|patch> [--allow-unpublished-deps]
//   order
//
// prepare flow (matches tools/scripts/release-js-lib.mjs):
//   1. Assert on develop, clean tree
//   2. Read current version from the project's version property in the root pom
//   3. Compute new version via semver bump
//   4. Assert every in-repo dependency's pinned version is resolvable on Maven Central
//   5. Pre-flight build
//   6. Create release/<project>/<newVersion> branch
//   7. Bump the property via versions-maven-plugin (single edit, root pom only)
//   8. Commit, push, open PR to develop
//
// order prints the release tiers derived from the in-repo dependency graph: a lib can only
// be released once every lib it depends on is already published, because the deployed pom
// pins those siblings by version and Central rejects nothing — it just serves a pom no
// external consumer can resolve.
//
// Design (why there's no finalize step):
//   The property doubles as (a) the lib's own artifact version and (b) the pin
//   siblings use in <dependencyManagement>. If it drifts to a -SNAPSHOT between
//   releases, every sibling depending on this lib becomes unreleasable because
//   the SNAPSHOT isn't on Central. So we keep the property at the LAST PUBLISHED
//   version between releases — no ceremony, next release just bumps.
//
// Preconditions:
//   - Root pom has a version property for the project, holding a plain X.Y.Z semver (no
//     -SNAPSHOT, no prereleases). Libs use <${project}.version>; processpuzzle-parent uses
//     <revision>, because Maven only honours `revision`/`sha1`/`changelist` as version
//     placeholders and the parent's <version> is itself the placeholder.
//   - The corresponding pom's <version> uses that property.
//   - flatten-maven-plugin resolves the property at publish time.

import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { parseArgs } from 'node:util';

const MVN = process.env.MVN ?? 'mvn';
const GH = process.env.GH ?? 'gh';

const JAVA_LIBS_DIR = 'libs/java-shared';
const CENTRAL_BASE = 'https://repo1.maven.org/maven2';
const GROUP_PATH = 'com/processpuzzle';

// project name -> { path, versionProperty }. The property name is not derivable from the
// project name for the parent: Maven only substitutes `revision`, `sha1` and `changelist`
// into a <version>, so the parent's version property has to be one of those.
const PROJECTS = new Map([
  ['processpuzzle-parent', { path: '.', versionProperty: 'revision' }],
  ...[
    'api-contracts',
    'base-app-backend',
    'base-document-backend',
    'base-entity-backend',
    'base-rule-backend',
    'base-state-backend',
    'base-widget-backend',
    'base-workflow-backend',
    'org-admin-backend',
    'processpuzzle-core',
    'processpuzzle-store',
  ].map((name) => [name, { path: `${JAVA_LIBS_DIR}/${name}`, versionProperty: `${name}.version` }]),
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

// --- pom parsing -----------------------------------------------------------------
// Regex, not a DOM: node has no bundled XML parser and adding a dependency for four
// element lookups isn't worth it. Comments are stripped first, and each helper removes the
// enclosing blocks it must not look inside before matching.

function stripComments(xml) {
  return xml.replace(/<!--[\s\S]*?-->/g, '');
}

function dropBlock(xml, tag) {
  return xml.replace(new RegExp(`<${tag}>[\\s\\S]*?</${tag}>`, 'g'), '');
}

function readRootProperties() {
  const xml = stripComments(readFileSync('pom.xml', 'utf8'));
  const block = xml.match(/<properties>([\s\S]*?)<\/properties>/);
  if (!block) {
    throw new Error('No <properties> block in root pom.xml');
  }
  const props = new Map();
  for (const m of block[1].matchAll(/<([\w.-]+)>([^<]*)<\/\1>/g)) {
    props.set(m[1], m[2].trim());
  }
  return props;
}

function resolveProperty(value, props) {
  const m = value.match(/^\$\{([^}]+)\}$/);
  return m ? props.get(m[1]) : value;
}

// artifactId -> pinned version, from the root pom's <dependencyManagement>. This is where
// the libs' sibling dependencies get their version from: they declare none of their own.
function readManagedVersions(props) {
  const xml = stripComments(readFileSync('pom.xml', 'utf8'));
  const block = xml.match(/<dependencyManagement>([\s\S]*?)<\/dependencyManagement>/);
  if (!block) {
    throw new Error('No <dependencyManagement> block in root pom.xml');
  }
  const managed = new Map();
  for (const dep of block[1].matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)) {
    const body = dep[1];
    const groupId = body.match(/<groupId>([^<]+)<\/groupId>/)?.[1]?.trim();
    const artifactId = body.match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim();
    const version = body.match(/<version>([^<]+)<\/version>/)?.[1]?.trim();
    if (!artifactId || !version) continue;
    if (groupId !== 'com.processpuzzle' && groupId !== '${project.groupId}') continue;
    managed.set(artifactId, resolveProperty(version, props));
  }
  return managed;
}

// Derives the in-repo dependency graph from the lib poms rather than hardcoding it, so it
// cannot drift. Returns project -> { path, artifactId, versionProperty, version, deps }.
// Note the project -> artifactId mapping is not identity: api-contracts -> processpuzzle-api.
function buildJavaGraph() {
  const props = readRootProperties();
  const managed = readManagedVersions(props);
  const graph = new Map();

  for (const dir of readdirSync(JAVA_LIBS_DIR)) {
    const path = `${JAVA_LIBS_DIR}/${dir}`;
    const pomPath = `${path}/pom.xml`;
    if (!existsSync(pomPath)) continue;

    const raw = stripComments(readFileSync(pomPath, 'utf8'));
    // <parent> holds an artifactId and a version that are not the project's own; <build>,
    // <dependencyManagement> and <profiles> hold dependencies that are not the project's.
    const own = dropBlock(dropBlock(dropBlock(dropBlock(raw, 'parent'), 'dependencyManagement'), 'build'), 'profiles');

    const artifactId = own.match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim();
    const versionRef = own.match(/<version>([^<]+)<\/version>/)?.[1]?.trim();
    if (!artifactId || !versionRef) {
      throw new Error(`Could not read artifactId/version from ${pomPath}`);
    }
    const versionProperty = versionRef.match(/^\$\{([^}]+)\}$/)?.[1];
    if (!versionProperty) {
      throw new Error(`${pomPath}: expected <version> to reference a property, got '${versionRef}'`);
    }

    const deps = [];
    for (const dep of own.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)) {
      const body = dep[1];
      const groupId = body.match(/<groupId>([^<]+)<\/groupId>/)?.[1]?.trim();
      if (groupId !== 'com.processpuzzle' && groupId !== '${project.groupId}') continue;
      const depArtifact = body.match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim();
      const declared = body.match(/<version>([^<]+)<\/version>/)?.[1]?.trim();
      const version = declared ? resolveProperty(declared, props) : managed.get(depArtifact);
      if (!version) {
        throw new Error(
          `${pomPath}: dependency ${depArtifact} has no version, and none is managed in the root pom`,
        );
      }
      deps.push({ artifactId: depArtifact, version });
    }

    graph.set(dir, {
      path,
      artifactId,
      versionProperty,
      version: props.get(versionProperty),
      deps,
    });
  }

  // Resolve each dependency's artifactId back to the project that produces it, so callers
  // can talk in project names (which is what --project takes).
  const byArtifact = new Map([...graph].map(([project, info]) => [info.artifactId, project]));
  for (const info of graph.values()) {
    for (const dep of info.deps) {
      dep.project = byArtifact.get(dep.artifactId);
    }
  }
  return graph;
}

// Kahn's algorithm, grouping by level so each tier can be released in parallel.
function releaseTiers(graph) {
  const remaining = new Map(
    [...graph].map(([project, info]) => [
      project,
      new Set(info.deps.map((d) => d.project).filter((p) => p && graph.has(p))),
    ]),
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

function formatTiers(graph, tiers) {
  return tiers
    .map((tier, i) => {
      const entries = tier.map((project) => {
        const info = graph.get(project);
        const suffix = info.artifactId === project ? '' : ` (${info.artifactId})`;
        return `${project}${suffix} ${info.version}`;
      });
      return `  ${i + 1}. ${entries.join(', ')}`;
    })
    .join('\n');
}

function order() {
  const graph = buildJavaGraph();
  const tiers = releaseTiers(graph);
  console.log(
    `Java release order — derived from the in-repo dependencies in ${JAVA_LIBS_DIR}/*/pom.xml.\n` +
      `Release a tier only once every lib in the earlier tiers is on Maven Central.\n`,
  );
  console.log(formatTiers(graph, tiers));
  console.log(
    `\nprocesspuzzle-parent has no in-repo dependencies and can be released at any point.`,
  );
}

// --- Maven Central resolvability -------------------------------------------------

function centralPomUrl(artifactId, version) {
  return `${CENTRAL_BASE}/${GROUP_PATH}/${artifactId}/${version}/${artifactId}-${version}.pom`;
}

async function isOnCentral(artifactId, version) {
  const url = centralPomUrl(artifactId, version);
  try {
    const res = await fetch(url, {
      method: 'HEAD',
      redirect: 'follow',
      signal: AbortSignal.timeout(20_000),
    });
    return res.ok;
  } catch (err) {
    // Don't guess: an unreachable Central is not the same answer as a 404, and treating it
    // as one would either block every release offline or wave a broken pom through.
    throw new Error(
      `Could not reach Maven Central (${url}): ${err.message}\n` +
        `Retry, or pass --allow-unpublished-deps if you have verified the pins by hand.`,
      { cause: err },
    );
  }
}

// CI installs a lib's in-repo dependencies into ~/.m2 before deploying (see
// .github/actions/release-java/action.yml), so a dependency that is NOT on Central resolves
// happily during the release and then 404s for every external consumer of the published pom.
// This is the check that catches it, before a branch exists.
async function assertDependenciesOnCentral(project, graph) {
  const info = graph.get(project);
  if (!info || !info.deps.length) {
    console.log('No in-repo dependencies — skipping the Maven Central resolvability check.');
    return;
  }

  console.log(`Checking ${info.deps.length} in-repo dependencies against Maven Central...`);
  // Sequential: a handful of requests at most, and concurrent HEADs against repo1 proved
  // flaky here (connect timeouts) where one-at-a-time is reliable.
  const results = [];
  for (const dep of info.deps) {
    const published = await isOnCentral(dep.artifactId, dep.version);
    console.log(`  ${published ? 'ok     ' : 'MISSING'} ${dep.artifactId}:${dep.version}`);
    results.push({ ...dep, published });
  }

  const missing = results.filter((d) => !d.published);
  if (!missing.length) return;

  const tiers = releaseTiers(graph);
  throw new Error(
    `${project} depends on ${missing.length} artifact(s) that are not on Maven Central:\n` +
      missing.map((d) => `  - ${d.artifactId}:${d.version}\n    ${centralPomUrl(d.artifactId, d.version)}`).join('\n') +
      `\n\nReleasing now would publish a pom no external consumer can resolve. Release the\n` +
      `missing libs first, in this order:\n\n${formatTiers(graph, tiers)}\n\n` +
      `Pass --allow-unpublished-deps to override (the in-repo build still succeeds, because\n` +
      `CI installs siblings into ~/.m2 — external consumers are what break).`,
  );
}

// --- release ---------------------------------------------------------------------

function readCurrentVersion(project) {
  const { versionProperty } = PROJECTS.get(project);
  const pom = readFileSync('pom.xml', 'utf8');
  const re = new RegExp(`<${versionProperty}>([^<]+)</${versionProperty}>`);
  const m = pom.match(re);
  if (!m) {
    throw new Error(`Property <${versionProperty}> not found in root pom.xml`);
  }
  return m[1].trim();
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
  const { versionProperty } = PROJECTS.get(project);
  run(MVN, [
    '-N',
    'versions:set-property',
    `-Dproperty=${versionProperty}`,
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
  const entry = PROJECTS.get(project);
  if (!entry) {
    throw new Error(
      `Unknown Java project '${project}'. Known: ${[...PROJECTS.keys()].join(', ')}`,
    );
  }
  if (!existsSync(`${entry.path}/pom.xml`)) {
    throw new Error(`Expected ${entry.path}/pom.xml to exist`);
  }
  return entry.path;
}

// The parent has no Nx project (the root project.json is @processpuzzle/main, unrelated), so
// it gets a non-recursive validate instead of an Nx build.
function preflightBuild(project) {
  console.log('Pre-release safety check: building the target...');
  if (project === 'processpuzzle-parent') {
    run(MVN, ['-N', 'validate']);
  } else {
    run(process.env.NPX ?? 'npx', ['nx', 'run', `${project}:build`]);
  }
}

async function prepare(project, increment, allowUnpublishedDeps) {
  projectPath(project);
  assertOnBranch('develop');
  assertCleanTree();

  const current = readCurrentVersion(project);
  const newVersion = bumpVersion(current, increment);
  const branch = `release/${project}/${newVersion}`;

  console.log(`\n>>> Releasing ${project}: ${current} -> ${newVersion} (${increment})\n`);

  if (allowUnpublishedDeps) {
    console.log('--allow-unpublished-deps: skipping the Maven Central resolvability check.');
  } else {
    await assertDependenciesOnCentral(project, buildJavaGraph());
  }

  preflightBuild(project);

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
    `  node tools/scripts/release-java-lib.mjs prepare --project <name> --increment <major|minor|patch>\n` +
    `      [--allow-unpublished-deps]   release even if an in-repo dependency is not on Maven Central\n` +
    `  node tools/scripts/release-java-lib.mjs order\n\n` +
    `Known projects:\n  ${[...PROJECTS.keys()].join('\n  ')}\n`
  );
}

async function main() {
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
        'allow-unpublished-deps': { type: 'boolean', default: false },
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

  await prepare(values.project, values.increment, values['allow-unpublished-deps']);
}

try {
  await main();
} catch (err) {
  console.error(`\nERROR: ${err.message}`);
  process.exit(1);
}
