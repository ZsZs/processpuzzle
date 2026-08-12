import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  DOCUMENTS_FILE_SUFFIX,
  findRepositoryRoot,
  listSampleDocumentFiles,
  parseSampleDocuments,
  readSampleDocumentFile,
  resolveSampleDocumentsDir,
  SampleDocumentError,
  seedSampleDocuments,
  type SampleDocumentFile,
} from './sample-documents.js';
import { createDocumentStoreStub, ORG_KEY, seedDocument } from './test-support.js';

const aFile = (documents: unknown[], orgKey = ORG_KEY): SampleDocumentFile => parseSampleDocuments(`${orgKey}${DOCUMENTS_FILE_SUFFIX}`, JSON.stringify({ documents }));

const aDocument = (overrides: Record<string, unknown> = {}) => ({ slug: 'q3-plan', title: 'Q3 plan', sourceLocale: 'en', ...overrides });

const aDirectoryWith = (files: Record<string, string>): string => {
  const directory = mkdtempSync(join(tmpdir(), 'sample-documents-'));
  Object.entries(files).forEach(([name, content]) => writeFileSync(join(directory, name), content, 'utf-8'));
  return directory;
};

describe('parseSampleDocuments', () => {
  it('takes the organization from the file name', () => {
    // YAML is a superset of JSON, so a JSON body exercises the same parser with less quoting noise.
    expect(parseSampleDocuments('acme-documents.yaml', JSON.stringify({ documents: [aDocument()] })).orgKey).toBe('acme');
  });

  it('parses yaml, not only json', () => {
    const file = parseSampleDocuments('acme-documents.yaml', 'documents:\n  - slug: q3-plan\n    title: Q3 plan\n    sourceLocale: en\n');

    expect(file.documents).toEqual([{ slug: 'q3-plan', title: 'Q3 plan', sourceLocale: 'en' }]);
  });

  it.each([
    ['a file name that names no tenant', DOCUMENTS_FILE_SUFFIX, JSON.stringify({ documents: [] })],
    ['a file name with another suffix', 'acme-rules.yaml', JSON.stringify({ documents: [] })],
    ['a body without a documents list', 'acme-documents.yaml', JSON.stringify({ rules: [] })],
    ['an empty body', 'acme-documents.yaml', ''],
    ['a documents entry that is not a mapping', 'acme-documents.yaml', JSON.stringify({ documents: ['q3-plan'] })],
    ['a document without a slug', 'acme-documents.yaml', JSON.stringify({ documents: [{ title: 'Q3 plan', sourceLocale: 'en' }] })],
    ['a document with a blank title', 'acme-documents.yaml', JSON.stringify({ documents: [aDocument({ title: '  ' })] })],
    ['a document without a source locale', 'acme-documents.yaml', JSON.stringify({ documents: [{ slug: 'q3-plan', title: 'Q3 plan' }] })],
    ['translations that are not a list', 'acme-documents.yaml', JSON.stringify({ documents: [aDocument({ translations: { locale: 'en' } })] })],
    ['a translation without a locale', 'acme-documents.yaml', JSON.stringify({ documents: [aDocument({ translations: [{ blocks: [] }] })] })],
    ['the same locale twice', 'acme-documents.yaml', JSON.stringify({ documents: [aDocument({ translations: [{ locale: 'en' }, { locale: 'en' }] })] })],
  ])('rejects %s', (_case, fileName, body) => {
    expect(() => parseSampleDocuments(fileName, body)).toThrow(SampleDocumentError);
  });

  it('accepts a document without translations', () => {
    expect(aFile([aDocument({ translations: null })]).documents).toHaveLength(1);
  });
});

describe('seedSampleDocuments', () => {
  it('imports a document and its source-locale draft, as createDocument would', async () => {
    const stub = createDocumentStoreStub();

    const outcome = await seedSampleDocuments(stub.store, aFile([aDocument({ title: 'Q3 plan', isPublic: true, translations: [{ locale: 'en', blocks: [{ id: 'intro', kind: 'TEXT' }] }] })]));

    expect(outcome).toEqual({ orgKey: ORG_KEY, imported: ['q3-plan'], skipped: [], deleted: 0 });
    const [document] = [...stub.documents.values()];
    expect(document).toMatchObject({ orgKey: ORG_KEY, slug: 'q3-plan', title: 'Q3 plan', isPublic: true, lockVersion: 0, createdBy: null });
    expect([...stub.drafts.values()]).toEqual([
      { locale: 'en', blocks: [{ id: 'intro', kind: 'TEXT' }], revision: 1, basedOnRevision: null, createdAt: document.createdAt, updatedAt: document.createdAt },
    ]);
  });

  it('leaves publication to the operations that own it', async () => {
    const stub = createDocumentStoreStub();

    await seedSampleDocuments(stub.store, aFile([aDocument({ isPublic: true })]));

    expect([...stub.documents.values()][0].publishedAt).toBeNull();
    expect(stub.published.size).toBe(0);
  });

  it('preserves declared block ids, so widget embeds keep resolving', async () => {
    const stub = createDocumentStoreStub();
    const blocks = [
      { id: 'event-flow', kind: 'TEXT', content: { type: 'doc', content: [{ type: 'widgetEmbed', attrs: { blockId: 'order-highlight' } }] } },
      { id: 'order-highlight', kind: 'WIDGET', placement: 'REFERENCED', type: 'entity-form' },
    ];

    await seedSampleDocuments(stub.store, aFile([aDocument({ translations: [{ locale: 'en', blocks }] })]));

    expect([...stub.drafts.values()][0].blocks.map((block) => block.id)).toEqual(['event-flow', 'order-highlight']);
  });

  it('branches every other locale off the source draft revision', async () => {
    const stub = createDocumentStoreStub();
    const translations = [
      { locale: 'hu', blocks: [{ id: 'intro-hu', kind: 'TEXT' }] },
      { locale: 'en', blocks: [{ id: 'intro', kind: 'TEXT' }] },
    ];

    // Source locale declared last on purpose: the source draft has to be written first regardless of
    // the file's order, because the others record its revision.
    await seedSampleDocuments(stub.store, aFile([aDocument({ sourceLocale: 'en', translations })]));

    expect([...stub.drafts.values()].map((draft) => [draft.locale, draft.basedOnRevision])).toEqual([
      ['en', null],
      ['hu', 1],
    ]);
  });

  it('copies the source blocks into a translation that declares none', async () => {
    const stub = createDocumentStoreStub();
    const translations = [{ locale: 'en', blocks: [{ id: 'intro', kind: 'TEXT' }] }, { locale: 'hu' }];

    await seedSampleDocuments(stub.store, aFile([aDocument({ translations })]));

    const hungarian = [...stub.drafts.values()].find((draft) => draft.locale === 'hu');
    expect(hungarian?.blocks.map((block) => block.id)).toEqual(['intro']);
  });

  it('starts a translation that declares an empty list blank', async () => {
    const stub = createDocumentStoreStub();
    const translations = [
      { locale: 'en', blocks: [{ id: 'intro', kind: 'TEXT' }] },
      { locale: 'hu', blocks: [] },
    ];

    await seedSampleDocuments(stub.store, aFile([aDocument({ translations })]));

    expect([...stub.drafts.values()].find((draft) => draft.locale === 'hu')?.blocks).toEqual([]);
  });

  it('creates the source-locale draft even when the file declares no translation for it', async () => {
    const stub = createDocumentStoreStub();

    await seedSampleDocuments(stub.store, aFile([aDocument({ sourceLocale: 'en', translations: [] })]));

    expect([...stub.drafts.values()]).toMatchObject([{ locale: 'en', blocks: [] }]);
  });

  it('skips a slug that already exists, so an edited sample survives a re-run', async () => {
    const stub = createDocumentStoreStub();
    const existing = seedDocument(stub, aStoredSample('q3-plan'));

    const outcome = await seedSampleDocuments(stub.store, aFile([aDocument({ slug: 'q3-plan', title: 'Renamed by the loader' })]));

    expect(outcome).toMatchObject({ imported: [], skipped: ['q3-plan'], deleted: 0 });
    expect(stub.documents.size).toBe(1);
    expect([...stub.documents.values()][0].title).toBe(existing.title);
  });

  it('deletes the organization documents first when reset is set', async () => {
    const stub = createDocumentStoreStub();
    seedDocument(stub, aStoredSample('left-over-by-an-interrupted-e2e-run'));
    seedDocument(stub, aStoredSample('q3-plan'));

    const outcome = await seedSampleDocuments(stub.store, aFile([aDocument({ slug: 'q3-plan' })]), { reset: true });

    expect(outcome).toMatchObject({ imported: ['q3-plan'], skipped: [], deleted: 2 });
    expect([...stub.documents.values()].map((document) => document.slug)).toEqual(['q3-plan']);
  });

  it('does not touch another organization on reset', async () => {
    const stub = createDocumentStoreStub();
    seedDocument(stub, { ...aStoredSample('acme-plan'), orgKey: 'acme' });

    await seedSampleDocuments(stub.store, aFile([aDocument()], ORG_KEY), { reset: true });

    expect([...stub.documents.values()].filter((document) => document.orgKey === 'acme')).toHaveLength(1);
  });
});

describe('the sample files shipped with base-document-backend', () => {
  it('are the ones both platforms seed from', () => {
    const directory = resolveSampleDocumentsDir();

    expect(listSampleDocumentFiles(directory)).toContain(`${ORG_KEY}${DOCUMENTS_FILE_SUFFIX}`);
  });

  it('seed without a hand-written fixture', async () => {
    const stub = createDocumentStoreStub();
    const file = readSampleDocumentFile(resolveSampleDocumentsDir(), `${ORG_KEY}${DOCUMENTS_FILE_SUFFIX}`);

    const outcome = await seedSampleDocuments(stub.store, file, { reset: true });

    // The point of the assertion is that the Documents list is not empty afterwards — which is the
    // e2e expectation this seeding exists to satisfy — not which samples happen to be in the file.
    expect(outcome.imported.length).toBeGreaterThan(0);
    expect(stub.documents.size).toBe(outcome.imported.length);
    expect([...stub.drafts.values()].length).toBeGreaterThanOrEqual(stub.documents.size);
  });
});

describe('locating the samples', () => {
  it('finds the repository root by its nx.json', () => {
    expect(resolveSampleDocumentsDir()).toBe(join(findRepositoryRoot(), 'libs', 'java-shared', 'base-document-backend', 'src', 'main', 'resources', 'sample-documents'));
  });

  it('reports where it looked when there is no nx.json above', () => {
    expect(() => findRepositoryRoot(mkdtempSync(join(tmpdir(), 'not-a-repository-')))).toThrow(SampleDocumentError);
  });
});

describe('listSampleDocumentFiles', () => {
  it('returns only sample document files, in name order', () => {
    const directory = aDirectoryWith({
      'zulu-documents.yaml': 'documents: []',
      'acme-documents.yaml': 'documents: []',
      'acme-rules.yaml': 'rules: []',
      'README.md': '',
    });

    expect(listSampleDocumentFiles(directory)).toEqual(['acme-documents.yaml', 'zulu-documents.yaml']);
  });

  it('rejects a directory that does not exist', () => {
    expect(() => listSampleDocumentFiles(join(tmpdir(), 'no-such-sample-documents-directory'))).toThrow(SampleDocumentError);
  });
});

/** A stored document as the store already holds it, i.e. before this run — only the slug matters. */
function aStoredSample(slug: string) {
  const timestamp = '2026-01-01T00:00:00.000Z';
  return {
    id: `id-of-${slug}`,
    orgKey: ORG_KEY,
    slug,
    title: `Existing ${slug}`,
    subject: null,
    description: null,
    author: null,
    sourceLocale: 'en',
    isPublic: false,
    readerRoles: [],
    editorRoles: [],
    publisherRoles: [],
    inputPorts: [],
    outputPorts: [],
    lockVersion: 0,
    createdBy: null,
    createdAt: timestamp,
    publishedAt: null,
    updatedAt: timestamp,
  };
}
