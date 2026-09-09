import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FIXTURE_ROOT = path.resolve(__dirname, '../fixtures');

export interface FixtureRecord<T = unknown> {
  /** Absolute path on disk. */
  fixturePath: string;
  /** Path as passed in, relative to /fixtures — the stable key used everywhere else. */
  relativePath: string;
  fileName: string;
  /** Raw YAML text, exactly as written — this is what the doc generator embeds verbatim. */
  raw: string;
  data: T;
}

const cache = new Map<string, FixtureRecord>();

/**
 * Load a fixture's parsed data only. Most test code wants this.
 */
export function loadMetadataFixture<T = unknown>(relativePath: string): T {
  return loadMetadataFixtureRecord<T>(relativePath).data;
}

/**
 * Load a fixture with its raw text and metadata intact — used by the doc
 * generator to reproduce the exact YAML alongside the narrative, and by
 * tests that only need the parsed data via loadMetadataFixture().
 */
export function loadMetadataFixtureRecord<T = unknown>(relativePath: string): FixtureRecord<T> {
  const normalized = relativePath.replace(/^\/+/, '');
  if (cache.has(normalized)) {
    return cache.get(normalized) as FixtureRecord<T>;
  }

  const fixturePath = path.join(FIXTURE_ROOT, normalized);
  if (!fs.existsSync(fixturePath)) {
    throw new Error(`Metadata fixture not found: ${fixturePath}`);
  }

  const raw = fs.readFileSync(fixturePath, 'utf-8');
  const data = YAML.parse(raw) as T;

  const record: FixtureRecord<T> = {
    fixturePath,
    relativePath: normalized,
    fileName: path.basename(fixturePath),
    raw,
    data,
  };
  cache.set(normalized, record);
  return record;
}

/** Mainly for tests of the loader itself. */
export function resetFixtureCache(): void {
  cache.clear();
}
