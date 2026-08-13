import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { StoredDraft } from './base-document.model.js';

const { getApps, initializeApp, getFirestore } = vi.hoisted(() => ({
  getApps: vi.fn<() => unknown[]>(),
  initializeApp: vi.fn(),
  getFirestore: vi.fn(),
}));

vi.mock('firebase-admin/app', () => ({ getApps, initializeApp }));
vi.mock('firebase-admin/firestore', () => ({ getFirestore }));

const { FirestoreDocumentStore } = await import('./document-store.js');
const { aStoredDocument, aStoredDraft, aTextBlock, ORG_KEY } = await import('./test-support.js');

const DOCUMENT_ID = '11111111-1111-1111-1111-111111111111';
const documentPath = (documentId = DOCUMENT_ID) => `organizations/${ORG_KEY}/documents/${documentId}`;

interface Filter {
  field: string;
  value: unknown;
}

/**
 * Minimal in-process stand-in for the Firestore driver, keyed by full document path. It exists to
 * assert the *paths* and *call shapes* this store produces — the layout is the tenant-isolation
 * mechanism, so a typo in a collection name is the failure mode worth catching here.
 */
function createFakeFirestore() {
  const data = new Map<string, Record<string, unknown>>();

  const docRef = (path: string) => ({
    path,
    get: async () => ({ exists: data.has(path), data: () => data.get(path) }),
    set: async (value: Record<string, unknown>) => void data.set(path, value),
    delete: async () => void data.delete(path),
    collection: (name: string) => collectionRef(`${path}/${name}`),
  });

  const collectionRef = (path: string, filters: Filter[] = [], max = Number.POSITIVE_INFINITY): Record<string, unknown> => {
    const self = {
      path,
      doc: (id: string) => docRef(`${path}/${id}`),
      withConverter: () => self,
      where: (field: string, _operator: string, value: unknown) => collectionRef(path, [...filters, { field, value }], max),
      limit: (count: number) => collectionRef(path, filters, count),
      get: async () => {
        const docs = [...data.entries()]
          // Direct children only: a subcollection entry shares the prefix but is one segment deeper.
          .filter(([key]) => key.startsWith(`${path}/`) && !key.slice(path.length + 1).includes('/'))
          .filter(([, value]) => filters.every((filter) => value[filter.field] === filter.value))
          .slice(0, max)
          .map(([key, value]) => ({ data: () => value, ref: docRef(key) }));
        return { docs, empty: docs.length === 0 };
      },
    };
    return self as unknown as Record<string, unknown>;
  };

  const deletions: string[] = [];

  return {
    data,
    deletions,
    db: {
      collection: (path: string) => collectionRef(path),
      batch: () => ({
        delete: (ref: { path: string }) => void deletions.push(ref.path),
        commit: async () => deletions.forEach((path) => data.delete(path)),
      }),
      runTransaction: async <T>(
        body: (transaction: { get: (ref: { path: string }) => Promise<unknown>; set: (ref: { path: string }, value: Record<string, unknown>) => void }) => Promise<T>,
      ): Promise<T> =>
        body({
          get: async (ref) => ({ exists: data.has(ref.path), data: () => data.get(ref.path) }),
          set: (ref, value) => void data.set(ref.path, value),
        }),
    },
  };
}

let firestore: ReturnType<typeof createFakeFirestore>;
let store: InstanceType<typeof FirestoreDocumentStore>;

beforeEach(() => {
  vi.clearAllMocks();
  firestore = createFakeFirestore();
  getApps.mockReturnValue([{}]);
  getFirestore.mockReturnValue(firestore.db);
  store = new FirestoreDocumentStore();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('admin app initialization', () => {
  it('initializes the app when none exists yet', async () => {
    getApps.mockReturnValue([]);

    await store.findDocument(ORG_KEY, DOCUMENT_ID);

    expect(initializeApp).toHaveBeenCalled();
  });

  it('does not initialize a second app when one is already running', async () => {
    await store.findDocument(ORG_KEY, DOCUMENT_ID);

    expect(initializeApp).not.toHaveBeenCalled();
  });
});

describe('documents', () => {
  it('writes a document under its organization, which is what scopes it to the tenant', async () => {
    const document = aStoredDocument();

    await store.saveDocument(document);

    expect(firestore.data.get(documentPath())).toEqual(document);
  });

  it('reads a document back', async () => {
    await store.saveDocument(aStoredDocument());

    expect(await store.findDocument(ORG_KEY, DOCUMENT_ID)).toMatchObject({ id: DOCUMENT_ID, slug: 'q3-plan' });
  });

  it('answers undefined rather than throwing for a document that does not exist', async () => {
    expect(await store.findDocument(ORG_KEY, DOCUMENT_ID)).toBeUndefined();
  });

  it('cannot see another organization document, because the path differs', async () => {
    await store.saveDocument(aStoredDocument({ orgKey: 'other-org' }));

    expect(await store.findDocument(ORG_KEY, DOCUMENT_ID)).toBeUndefined();
    expect(await store.findDocument('other-org', DOCUMENT_ID)).toBeDefined();
  });

  it('finds by slug within the organization', async () => {
    await store.saveDocument(aStoredDocument());

    expect(await store.findDocumentBySlug(ORG_KEY, 'q3-plan')).toMatchObject({ id: DOCUMENT_ID });
    expect(await store.findDocumentBySlug(ORG_KEY, 'absent')).toBeUndefined();
  });

  it('lists the organization documents up to the limit', async () => {
    await store.saveDocument(aStoredDocument({ id: 'doc-1', slug: 'one' }));
    await store.saveDocument(aStoredDocument({ id: 'doc-2', slug: 'two' }));

    expect(await store.listDocuments(ORG_KEY, 10)).toHaveLength(2);
    expect(await store.listDocuments(ORG_KEY, 1)).toHaveLength(1);
  });

  it('lists nothing for an organization with no documents', async () => {
    expect(await store.listDocuments(ORG_KEY, 10)).toEqual([]);
  });

  it('deletes the content before the document, so content is never left addressable by a ghost', async () => {
    await store.saveDocument(aStoredDocument());
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft());
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft({ locale: 'hu' }));
    firestore.data.set(`${documentPath()}/published/en`, { locale: 'en' });

    await store.deleteDocument(ORG_KEY, DOCUMENT_ID);

    expect(firestore.deletions[firestore.deletions.length - 1]).toBe(documentPath());
    expect(firestore.deletions).toContain(`${documentPath()}/published/en`);
    expect(firestore.deletions).toContain(`${documentPath()}/drafts/hu`);
    expect(firestore.data.has(documentPath())).toBe(false);
    expect(firestore.data.has(`${documentPath()}/drafts/en`)).toBe(false);
  });
});

describe('drafts', () => {
  it('keys a draft by locale beneath its document', async () => {
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft({ locale: 'hu' }));

    expect(firestore.data.has(`${documentPath()}/drafts/hu`)).toBe(true);
  });

  it('reads a draft back and answers undefined for a locale that has none', async () => {
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft());

    expect(await store.findDraft(ORG_KEY, DOCUMENT_ID, 'en')).toMatchObject({ locale: 'en' });
    expect(await store.findDraft(ORG_KEY, DOCUMENT_ID, 'de')).toBeUndefined();
  });

  it('lists every draft of the document', async () => {
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft());
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft({ locale: 'hu' }));

    expect((await store.listDrafts(ORG_KEY, DOCUMENT_ID)).map((draft) => draft.locale).sort()).toEqual(['en', 'hu']);
  });

  it('deletes one draft', async () => {
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft());

    await store.deleteDraft(ORG_KEY, DOCUMENT_ID, 'en');

    expect(await store.findDraft(ORG_KEY, DOCUMENT_ID, 'en')).toBeUndefined();
  });
});

describe('published snapshots', () => {
  const snapshot = { locale: 'en', blocks: [], publishedRevision: 1, publishedAt: '2026-02-01T00:00:00.000Z', publishedBy: null };

  it('reads and lists snapshots from their own subcollection, separate from drafts', async () => {
    firestore.data.set(`${documentPath()}/published/en`, snapshot);

    expect(await store.findPublished(ORG_KEY, DOCUMENT_ID, 'en')).toMatchObject({ locale: 'en' });
    expect(await store.listPublished(ORG_KEY, DOCUMENT_ID)).toHaveLength(1);
  });

  it('answers undefined for a locale that was never published', async () => {
    expect(await store.findPublished(ORG_KEY, DOCUMENT_ID, 'en')).toBeUndefined();
  });

  it('deletes a snapshot', async () => {
    firestore.data.set(`${documentPath()}/published/en`, snapshot);

    await store.deletePublished(ORG_KEY, DOCUMENT_ID, 'en');

    expect(await store.findPublished(ORG_KEY, DOCUMENT_ID, 'en')).toBeUndefined();
  });
});

describe('mutateDraftBlocks', () => {
  it('reports a missing draft instead of creating one', async () => {
    const outcome = await store.mutateDraftBlocks(ORG_KEY, DOCUMENT_ID, 'en', () => ({ ok: true, blocks: [], result: 'unused' }));

    expect(outcome).toEqual({ kind: 'draft-missing' });
    expect(firestore.data.has(`${documentPath()}/drafts/en`)).toBe(false);
  });

  it('applies the mutation, bumps the revision and stamps updatedAt', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-01T12:00:00.000Z'));
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft({ revision: 4 }));
    const block = aTextBlock('b1');

    const outcome = await store.mutateDraftBlocks(ORG_KEY, DOCUMENT_ID, 'en', (blocks) => ({ ok: true, blocks: [...blocks, block], result: block }));

    expect(outcome).toMatchObject({ kind: 'applied', result: block });
    const stored = firestore.data.get(`${documentPath()}/drafts/en`) as unknown as StoredDraft;
    expect(stored.revision).toBe(5);
    expect(stored.blocks).toEqual([block]);
    expect(stored.updatedAt).toBe('2026-07-01T12:00:00.000Z');
  });

  it('writes nothing when the mutation declines, so a rejected edit cannot bump the revision', async () => {
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft({ revision: 4 }));

    const outcome = await store.mutateDraftBlocks(ORG_KEY, DOCUMENT_ID, 'en', () => ({ ok: false, failure: 'block-referenced', detail: ['text-1'] }));

    expect(outcome).toEqual({ kind: 'failed', failure: 'block-referenced', detail: ['text-1'] });
    expect((firestore.data.get(`${documentPath()}/drafts/en`) as unknown as StoredDraft).revision).toBe(4);
  });

  it('hands the mutation an empty list when the draft has no blocks field at all', async () => {
    firestore.data.set(`${documentPath()}/drafts/en`, { locale: 'en', revision: 1, basedOnRevision: null });
    const seen: unknown[] = [];

    await store.mutateDraftBlocks(ORG_KEY, DOCUMENT_ID, 'en', (blocks) => {
      seen.push(blocks);
      return { ok: true, blocks, result: undefined };
    });

    expect(seen).toEqual([[]]);
  });

  it('runs inside a transaction, which is what keeps two concurrent autosaves from losing a block', async () => {
    const runTransaction = vi.spyOn(firestore.db, 'runTransaction');
    await store.saveDraft(ORG_KEY, DOCUMENT_ID, aStoredDraft());

    await store.mutateDraftBlocks(ORG_KEY, DOCUMENT_ID, 'en', (blocks) => ({ ok: true, blocks, result: undefined }));

    expect(runTransaction).toHaveBeenCalledOnce();
  });
});
