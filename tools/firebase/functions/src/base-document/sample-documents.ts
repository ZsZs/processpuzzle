import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { parse } from 'yaml';
import { MAX_LIST_SCAN } from './base-document.config.js';
import type { DocumentInput, DocumentTranslationInput, StoredDocument } from './base-document.model.js';
import * as mapper from './document-mapper.js';
import type { DocumentStore } from './document-store.js';

/**
 * Firebase-side counterpart of `SampleDocumentLoader`: imports the bundled sample documents so a
 * deployed environment has something to read and edit instead of an empty Documents list.
 *
 * The two topologies seed from **the same files** — `base-document-backend`'s
 * `sample-documents/<orgKey>-documents.yaml` — for the reason the platform runs both in CI at all: a
 * sample that exists on only one of them makes the Documents page a different feature depending on
 * where it is deployed. Adding a tenant is adding a file here too, exactly as the Java loader
 * documents.
 *
 * <h2>Why this is a deploy-time script rather than a function</h2>
 *
 * The Java loader runs on `ApplicationReadyEvent`, which a Cloud Function has no equivalent of: it is
 * started per request, by any of an unbounded number of instances, so "on startup" would mean "on
 * every cold start, concurrently". Seeding is therefore an explicit step of the deploy workflow,
 * which also gives it something the loader cannot have — see {@link SeedOptions.reset}.
 *
 * <h2>What is deliberately not seeded</h2>
 *
 * Publication. The Java loader publishes every locale of a public sample, because there the public
 * read path serves snapshots. In this topology publish, unpublish and `getPublishedContent` are the
 * operations `BaseDocumentHandlers` defers, so a `published` snapshot written here would be read by
 * nothing, while `deriveStatus` would start reporting `PUBLISHED` for content no endpoint can serve.
 * Samples are seeded as drafts, and `publishedAt` stays null until publishing exists.
 */

/** Same convention as `<orgKey>-rules.yaml` and `<orgKey>-apps.yaml`: the owning tenant is the file name. */
export const DOCUMENTS_FILE_SUFFIX = '-documents.yaml';

/** Where both platforms' samples live, relative to the repository root. */
export const SAMPLE_DOCUMENTS_DIR = join('libs', 'java-shared', 'base-document-backend', 'src', 'main', 'resources', 'sample-documents');

export class SampleDocumentError extends Error {}

export interface SampleDocumentFile {
  /** Taken from the file name, never from the file's content — one file, one tenant. */
  readonly orgKey: string;
  readonly fileName: string;
  readonly documents: readonly DocumentInput[];
}

export interface SeedOptions {
  /**
   * Delete every document of the organization before importing, making the environment's document
   * state a function of the repository alone.
   *
   * This is what "stage is reinitialized by every deployment" means, and it is the counterpart of
   * restarting the docker-compose topology, whose database starts empty. It also collects what an
   * interrupted e2e run leaves behind, which nothing else does: the CRUD suite deletes its own
   * fixtures, but only when it reaches teardown.
   *
   * Left false, seeding is additive and idempotent — a slug that already exists is skipped, so an
   * environment where someone has edited a sample keeps their edit.
   */
  readonly reset?: boolean;
}

export interface SeedOutcome {
  readonly orgKey: string;
  /** Slugs imported by this run. */
  readonly imported: readonly string[];
  /** Slugs already present and therefore left alone; always empty when `reset` is set. */
  readonly skipped: readonly string[];
  readonly deleted: number;
}

/**
 * Walks up from `from` to the directory holding `nx.json`.
 *
 * The samples are addressed from the repository root rather than relative to this module, because
 * this module is loaded from `lib/` after compilation and from `src/` by its spec, and a
 * `../../..`-style path would have to be wrong in one of the two.
 */
export function findRepositoryRoot(from: string = process.cwd()): string {
  let current = resolve(from);

  for (;;) {
    if (existsSync(join(current, 'nx.json'))) return current;
    const parent = dirname(current);
    if (parent === current) throw new SampleDocumentError(`No nx.json in '${resolve(from)}' or any parent; cannot locate ${SAMPLE_DOCUMENTS_DIR}.`);
    current = parent;
  }
}

export function resolveSampleDocumentsDir(from?: string): string {
  return join(findRepositoryRoot(from), SAMPLE_DOCUMENTS_DIR);
}

/** The sample files of `directory`, in name order so a run's log is comparable to the previous one's. */
export function listSampleDocumentFiles(directory: string): string[] {
  if (!existsSync(directory)) throw new SampleDocumentError(`Sample documents directory '${directory}' does not exist.`);
  return readdirSync(directory)
    .filter((name) => name.endsWith(DOCUMENTS_FILE_SUFFIX))
    .sort();
}

export function readSampleDocumentFile(directory: string, fileName: string): SampleDocumentFile {
  return parseSampleDocuments(fileName, readFileSync(join(directory, fileName), 'utf-8'));
}

/**
 * Validates only what would otherwise be stored as a broken document: the three required properties
 * and the shape around them. Everything else is left to the same defaulting the API applies, since
 * `toStoredProperties` is what writes the row either way.
 *
 * Malformed input throws rather than being skipped with a warning: a sample file is ours, so a typo
 * in it is a build mistake, and a deploy that silently seeded one of two documents is worse than one
 * that stops and says which entry is wrong.
 */
export function parseSampleDocuments(fileName: string, yamlText: string): SampleDocumentFile {
  if (!fileName.endsWith(DOCUMENTS_FILE_SUFFIX)) throw new SampleDocumentError(`'${fileName}' is not a sample document file; the name must end with '${DOCUMENTS_FILE_SUFFIX}'.`);

  const orgKey = fileName.slice(0, -DOCUMENTS_FILE_SUFFIX.length);
  if (!orgKey) throw new SampleDocumentError(`'${fileName}' names no organization; the name must be '<orgKey>${DOCUMENTS_FILE_SUFFIX}'.`);

  const parsed: unknown = parse(yamlText);
  const documents = (parsed as { documents?: unknown } | null)?.documents;
  if (!Array.isArray(documents)) throw new SampleDocumentError(`'${fileName}' has no 'documents' list.`);

  documents.forEach((document, index) => validate(document, `${fileName} documents[${index}]`));
  return { orgKey, fileName, documents: documents as DocumentInput[] };
}

/**
 * Imports `file`'s documents into `orgKey`, creating each one exactly as `createDocument` and
 * `addDocumentTranslation` would: the source-locale draft first, then the other locales branched from
 * its revision, with the declared block ids preserved.
 *
 * Preserving them is not cosmetic. A `widgetEmbed` node names its widget block by id, and the sample
 * deliberately reuses the same widget ids across locales; minting fresh ones would leave every
 * embed pointing at nothing.
 */
export async function seedSampleDocuments(store: DocumentStore, file: SampleDocumentFile, options: SeedOptions = {}): Promise<SeedOutcome> {
  const deleted = options.reset ? await deleteAll(store, file.orgKey) : 0;
  const imported: string[] = [];
  const skipped: string[] = [];

  for (const input of file.documents) {
    if (!options.reset && (await store.findDocumentBySlug(file.orgKey, input.slug))) {
      skipped.push(input.slug);
      continue;
    }

    await importDocument(store, file.orgKey, input);
    imported.push(input.slug);
  }

  return { orgKey: file.orgKey, imported, skipped, deleted };
}

async function deleteAll(store: DocumentStore, orgKey: string): Promise<number> {
  const documents = await store.listDocuments(orgKey, MAX_LIST_SCAN);
  for (const document of documents) await store.deleteDocument(orgKey, document.id);
  return documents.length;
}

async function importDocument(store: DocumentStore, orgKey: string, input: DocumentInput): Promise<StoredDocument> {
  const document = mapper.toNewStoredDocument(orgKey, input, null);
  await store.saveDocument(document);

  const translations = input.translations ?? [];
  const source = translations.find((translation) => translation.locale === document.sourceLocale);
  const sourceBlocks = mapper.toBlocks(source?.blocks ?? []);
  const sourceDraft = mapper.newDraft(document.sourceLocale, sourceBlocks, null, document.createdAt);
  await store.saveDraft(orgKey, document.id, sourceDraft);

  for (const translation of translations.filter((candidate) => candidate.locale !== document.sourceLocale)) {
    // `blocks` absent copies the source locale, the same distinction `addDocumentTranslation` draws
    // between an absent list and an explicit empty one.
    const blocks = translation.blocks == null ? sourceBlocks : mapper.toBlocks(translation.blocks);
    await store.saveDraft(orgKey, document.id, mapper.newDraft(translation.locale, blocks, sourceDraft.revision, document.createdAt));
  }

  return document;
}

function validate(document: unknown, where: string): void {
  if (typeof document !== 'object' || document === null || Array.isArray(document)) throw new SampleDocumentError(`${where} is not a document.`);

  const candidate = document as Partial<DocumentInput>;
  for (const field of ['slug', 'title', 'sourceLocale'] as const) {
    if (typeof candidate[field] !== 'string' || candidate[field]?.trim() === '') throw new SampleDocumentError(`${where} has no '${field}'.`);
  }

  const translations: unknown = candidate.translations;
  if (translations === undefined || translations === null) return;
  if (!Array.isArray(translations)) throw new SampleDocumentError(`${where} has a 'translations' that is not a list.`);

  translations.forEach((translation, index) => {
    const locale = (translation as Partial<DocumentTranslationInput> | null)?.locale;
    if (typeof locale !== 'string' || locale.trim() === '') throw new SampleDocumentError(`${where} translations[${index}] has no 'locale'.`);
  });

  const locales = translations.map((translation) => (translation as DocumentTranslationInput).locale);
  const duplicate = locales.find((locale, index) => locales.indexOf(locale) !== index);
  if (duplicate) throw new SampleDocumentError(`${where} declares locale '${duplicate}' twice.`);
}
