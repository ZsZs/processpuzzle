#!/usr/bin/env node
/**
 * Packages Business Starters for the registry bucket, as CI runs it at release.
 * See docs/business-starters/business-starters-design.md §4–§5.
 *
 *   node tools/business-starters/package-starters.mjs \
 *     --source business-starters        # one folder per starter, each with manifest.yaml
 *     --out dist/business-starters      # becomes the bucket's content
 *     [--catalog <current catalog.json>] # what the bucket holds now; omitted means an empty registry
 *     [--published-at <ISO instant>]     # defaults to now
 *
 * For every starter whose manifest version is not in the current catalog it writes
 * `<id>/<version>/bundle.zip` and `<id>/<version>/entry.json` (plus `<id>/icon.*`), and it always writes the
 * merged `catalog.json`. A version the catalog already has is skipped when its bundle is byte-identical
 * — zips are built deterministically for exactly this — and refused when it is not: a published version is
 * immutable, so changed content needs a new version. Uploading `--out` to the bucket is the caller's step.
 *
 * The checks here are structural only (manifest fields, safe paths, listed files present). Whether the
 * definitions import is the dry-run gate's job, against a running backend, before the upload.
 */
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, extname, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { crc32, deflateRawSync } from 'node:zlib';
import { parse, stringify } from 'yaml';

export const CATALOG_VERSION = 1;
export const SUPPORTED_SCHEMA_VERSIONS = [1];
const CONTENT_GROUPS = ['entities', 'states', 'rules', 'widgets', 'documents', 'workflows', 'apps'];
const SAFE_PATH = /^[A-Za-z0-9_-][A-Za-z0-9._-]*(\/[A-Za-z0-9_-][A-Za-z0-9._-]*)*$/;
const ID = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const SEMVER = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/;

export class StarterError extends Error {}

export const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');

/** The manifest of one starter folder, validated, with the bundle-relative paths of every file it lists. */
export function readStarter(folder) {
  const manifestPath = join(folder, 'manifest.yaml');
  if (!existsSync(manifestPath)) throw new StarterError(`${folder}: no manifest.yaml`);
  const manifest = parse(readFileSync(manifestPath, 'utf8')) ?? {};
  const where = `${folder}/manifest.yaml`;

  if (!ID.test(manifest.id ?? '')) throw new StarterError(`${where}: 'id' must be a kebab-case slug`);
  if (!SEMVER.test(manifest.version ?? '')) throw new StarterError(`${where}: 'version' must be semver`);
  if (!manifest.name) throw new StarterError(`${where}: 'name' is required`);
  if (!SUPPORTED_SCHEMA_VERSIONS.includes(manifest.definitionSchemaVersion)) {
    throw new StarterError(`${where}: unsupported definitionSchemaVersion ${manifest.definitionSchemaVersion}`);
  }
  for (const removed of ['requires', 'seedData', 'install', 'conflictsWith']) {
    if (removed in manifest) throw new StarterError(`${where}: '${removed}' is not supported — a starter is all or nothing`);
  }

  const contents = manifest.contents ?? {};
  const files = [];
  for (const [group, paths] of Object.entries(contents)) {
    if (!CONTENT_GROUPS.includes(group)) throw new StarterError(`${where}: unknown contents group '${group}'`);
    files.push(...(paths ?? []));
  }
  if (files.length === 0) throw new StarterError(`${where}: 'contents' lists no definition file`);
  const extras = [manifest.icon, manifest.migration].filter(Boolean);
  for (const path of [...files, ...extras]) {
    if (!SAFE_PATH.test(path) || path.split('/').includes('..')) throw new StarterError(`${where}: unsafe path '${path}'`);
    if (!existsSync(join(folder, path))) throw new StarterError(`${where}: lists '${path}', which does not exist`);
  }
  return { manifest, files: [...new Set([...files, ...extras])].sort() };
}

/**
 * A zip of `entries` (path → bytes), deflated, byte-identical for identical input: entries sorted, a fixed
 * timestamp, no extra fields. Node has deflate and crc32 but no zip writer, and the format is small enough
 * not to warrant a dependency.
 */
export function zip(entries) {
  const DOS_TIME = 0; // 00:00:00
  const DOS_DATE = (2000 - 1980) << 9 | 1 << 5 | 1; // 2000-01-01
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const name of Object.keys(entries).sort()) {
    const data = entries[name];
    const nameBytes = Buffer.from(name, 'utf8');
    const deflated = deflateRawSync(data, { level: 9 });
    const crc = crc32(data);

    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4); // version needed
    local.writeUInt16LE(0x0800, 6); // UTF-8 names
    local.writeUInt16LE(8, 8); // deflate
    local.writeUInt16LE(DOS_TIME, 10);
    local.writeUInt16LE(DOS_DATE, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(deflated.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBytes.length, 26);
    local.writeUInt16LE(0, 28);
    locals.push(local, nameBytes, deflated);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4); // version made by
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(8, 10);
    central.writeUInt16LE(DOS_TIME, 12);
    central.writeUInt16LE(DOS_DATE, 14);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(deflated.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBytes.length, 28);
    central.writeUInt32LE(offset, 42);
    centrals.push(central, nameBytes);

    offset += local.length + nameBytes.length + deflated.length;
  }
  const centralSize = centrals.reduce((size, part) => size + part.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(Object.keys(entries).length, 8);
  end.writeUInt16LE(Object.keys(entries).length, 10);
  end.writeUInt32LE(centralSize, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, ...centrals, end]);
}

/** The bundle of one starter: its files, and its manifest with the integrity hashes CI is meant to write. */
export function bundleOf(folder) {
  const { manifest, files } = readStarter(folder);
  const entries = {};
  const hashes = {};
  for (const path of files) {
    entries[path] = readFileSync(join(folder, path));
    hashes[path] = sha256(entries[path]);
  }
  const packaged = { ...manifest, integrity: { algorithm: 'sha256', files: hashes } };
  entries['manifest.yaml'] = Buffer.from(stringify(packaged), 'utf8');
  const bytes = zip(entries);
  return { manifest, bytes, sha256: sha256(bytes) };
}

/** Packages every starter under `source` into `out`, merging into `catalog`; returns what it did. */
export function packageStarters({ source, out, catalog, publishedAt }) {
  const merged = structuredClone(catalog ?? { catalogVersion: CATALOG_VERSION, starters: [] });
  merged.catalogVersion = CATALOG_VERSION;
  merged.starters ??= [];
  const published = [];
  const skipped = [];

  const folders = readdirSync(source)
    .map((name) => join(source, name))
    .filter((path) => statSync(path).isDirectory() && existsSync(join(path, 'manifest.yaml')))
    .sort();
  for (const folder of folders) {
    const { manifest, bytes, sha256: hash } = bundleOf(folder);
    let starter = merged.starters.find((candidate) => candidate.id === manifest.id);
    const existing = starter?.versions?.find((version) => version.version === manifest.version);
    if (existing) {
      if (existing.sha256 !== hash) {
        throw new StarterError(`${manifest.id} ${manifest.version} is already published with different content; raise its version`);
      }
      skipped.push(`${manifest.id}@${manifest.version}`);
      continue;
    }

    const bundlePath = `${manifest.id}/${manifest.version}/bundle.zip`;
    const entry = {
      version: manifest.version,
      publishedAt,
      status: 'published',
      definitionSchemaVersion: manifest.definitionSchemaVersion,
      sha256: hash,
      bundle: bundlePath,
    };
    mkdirSync(join(out, dirname(bundlePath)), { recursive: true });
    writeFileSync(join(out, bundlePath), bytes);
    writeFileSync(join(out, manifest.id, manifest.version, 'entry.json'), JSON.stringify({ id: manifest.id, ...entry }, null, 2) + '\n');

    let icon = starter?.icon;
    if (manifest.icon) {
      icon = `${manifest.id}/icon${extname(manifest.icon)}`;
      copyFileSync(join(folder, manifest.icon), join(out, icon));
    }
    // The starter's own fields follow its newest manifest; a new version of an old starter may rename it.
    const fields = {
      id: manifest.id,
      name: manifest.name,
      description: manifest.description,
      author: manifest.author,
      license: manifest.license,
      category: manifest.category,
      tags: manifest.tags,
      icon,
    };
    if (!starter) {
      starter = { ...fields, versions: [] };
      merged.starters.push(starter);
    } else if (!starter.versions.some((version) => compareVersions(version.version, manifest.version) > 0)) {
      Object.assign(starter, fields);
    }
    starter.versions.push(entry);
    published.push(`${manifest.id}@${manifest.version}`);
  }

  merged.starters.sort((left, right) => left.id.localeCompare(right.id));
  mkdirSync(out, { recursive: true });
  writeFileSync(join(out, 'catalog.json'), JSON.stringify(merged, null, 2) + '\n');
  return { published, skipped, catalog: merged };
}

/** Same order as StarterVersions in base-starter-backend: numeric, a pre-release before its release. */
export function compareVersions(left, right) {
  const [leftCore, leftPre] = left.split(/-(.*)/s);
  const [rightCore, rightPre] = right.split(/-(.*)/s);
  const leftParts = leftCore.split('.').map(Number);
  const rightParts = rightCore.split('.').map(Number);
  for (let index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
    const difference = (leftParts[index] ?? 0) - (rightParts[index] ?? 0);
    if (difference !== 0) return difference;
  }
  if (leftPre === rightPre) return 0;
  if (leftPre === undefined) return 1;
  if (rightPre === undefined) return -1;
  return leftPre.localeCompare(rightPre);
}

function main(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 2) args[argv[index].replace(/^--/, '')] = argv[index + 1];
  if (!args.source || !args.out) {
    console.error('usage: package-starters.mjs --source <dir> --out <dir> [--catalog <catalog.json>] [--published-at <ISO>]');
    process.exit(2);
  }
  const catalog = args.catalog && existsSync(args.catalog) ? JSON.parse(readFileSync(args.catalog, 'utf8')) : undefined;
  try {
    const result = packageStarters({ source: args.source, out: args.out, catalog, publishedAt: args['published-at'] ?? new Date().toISOString() });
    console.log(`published: ${result.published.join(', ') || '—'}`);
    console.log(`unchanged: ${result.skipped.join(', ') || '—'}`);
  } catch (error) {
    if (!(error instanceof StarterError)) throw error;
    console.error(error.message);
    process.exit(1);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main(process.argv.slice(2));
