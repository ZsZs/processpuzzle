import type { DocumentBlock, StoredDocument, StoredDraft, StoredPublished } from './base-document.model.js';
import type { BlockMutation, BlockMutationOutcome, DocumentStore } from './document-store.js';

// `serve` is shared with the object-store specs rather than duplicated: binding an express app to an
// ephemeral port has exactly one correct implementation and both feature folders need it.
export { serve, type RunningApp } from '../object-store/test-support.js';

export interface DocumentStoreStub {
  readonly documents: Map<string, StoredDocument>;
  readonly drafts: Map<string, StoredDraft>;
  readonly published: Map<string, StoredPublished>;
  readonly store: DocumentStore;
  failWith(error: Error): void;
}

const documentKey = (orgKey: string, documentId: string) => `${orgKey}/${documentId}`;
const translationKey = (orgKey: string, documentId: string, locale: string) => `${orgKey}/${documentId}/${locale}`;

/**
 * In-memory stand-in for `FirestoreDocumentStore`. The handlers only ever see the `DocumentStore`
 * interface, so their behaviour can be pinned without Firestore, a network or an emulator.
 *
 * `mutateDraftBlocks` applies the mutation directly instead of in a transaction — the atomicity it
 * provides is Firestore's to guarantee and is asserted against the real driver in
 * `document-store.spec.ts`, not here.
 */
export function createDocumentStoreStub(): DocumentStoreStub {
  const documents = new Map<string, StoredDocument>();
  const drafts = new Map<string, StoredDraft>();
  const published = new Map<string, StoredPublished>();
  let failure: Error | undefined;

  const guard = () => {
    if (failure) throw failure;
  };

  const store: DocumentStore = {
    async listDocuments(orgKey: string, limit: number): Promise<StoredDocument[]> {
      guard();
      return [...documents.values()].filter((document) => document.orgKey === orgKey).slice(0, limit);
    },
    async findDocument(orgKey: string, documentId: string): Promise<StoredDocument | undefined> {
      guard();
      return documents.get(documentKey(orgKey, documentId));
    },
    async findDocumentBySlug(orgKey: string, slug: string): Promise<StoredDocument | undefined> {
      guard();
      return [...documents.values()].find((document) => document.orgKey === orgKey && document.slug === slug);
    },
    async saveDocument(document: StoredDocument): Promise<void> {
      guard();
      documents.set(documentKey(document.orgKey, document.id), document);
    },
    async deleteDocument(orgKey: string, documentId: string): Promise<void> {
      guard();
      documents.delete(documentKey(orgKey, documentId));
      const prefix = `${documentKey(orgKey, documentId)}/`;
      [...drafts.keys()].filter((key) => key.startsWith(prefix)).forEach((key) => drafts.delete(key));
      [...published.keys()].filter((key) => key.startsWith(prefix)).forEach((key) => published.delete(key));
    },
    async findDraft(orgKey: string, documentId: string, locale: string): Promise<StoredDraft | undefined> {
      guard();
      return drafts.get(translationKey(orgKey, documentId, locale));
    },
    async listDrafts(orgKey: string, documentId: string): Promise<StoredDraft[]> {
      guard();
      const prefix = `${documentKey(orgKey, documentId)}/`;
      return [...drafts.entries()].filter(([key]) => key.startsWith(prefix)).map(([, draft]) => draft);
    },
    async saveDraft(orgKey: string, documentId: string, draft: StoredDraft): Promise<void> {
      guard();
      drafts.set(translationKey(orgKey, documentId, draft.locale), draft);
    },
    async deleteDraft(orgKey: string, documentId: string, locale: string): Promise<void> {
      guard();
      drafts.delete(translationKey(orgKey, documentId, locale));
    },
    async findPublished(orgKey: string, documentId: string, locale: string): Promise<StoredPublished | undefined> {
      guard();
      return published.get(translationKey(orgKey, documentId, locale));
    },
    async listPublished(orgKey: string, documentId: string): Promise<StoredPublished[]> {
      guard();
      const prefix = `${documentKey(orgKey, documentId)}/`;
      return [...published.entries()].filter(([key]) => key.startsWith(prefix)).map(([, snapshot]) => snapshot);
    },
    async deletePublished(orgKey: string, documentId: string, locale: string): Promise<void> {
      guard();
      published.delete(translationKey(orgKey, documentId, locale));
    },
    async mutateDraftBlocks<T>(orgKey: string, documentId: string, locale: string, mutate: (blocks: DocumentBlock[]) => BlockMutation<T>): Promise<BlockMutationOutcome<T>> {
      guard();
      const key = translationKey(orgKey, documentId, locale);
      const draft = drafts.get(key);
      if (!draft) return { kind: 'draft-missing' };

      const mutation = mutate(draft.blocks ?? []);
      if (!mutation.ok) return { kind: 'failed', failure: mutation.failure, detail: mutation.detail };

      const updated: StoredDraft = { ...draft, blocks: mutation.blocks, revision: draft.revision + 1, updatedAt: new Date().toISOString() };
      drafts.set(key, updated);
      return { kind: 'applied', result: mutation.result, draft: updated };
    },
  };

  return {
    documents,
    drafts,
    published,
    store,
    failWith(error: Error) {
      failure = error;
    },
  };
}

export const ORG_KEY = 'processpuzzle-testbed';

export function aStoredDocument(overrides: Partial<StoredDocument> = {}): StoredDocument {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    orgKey: ORG_KEY,
    slug: 'q3-plan',
    title: 'Q3 plan',
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
    createdAt: '2026-01-01T00:00:00.000Z',
    publishedAt: null,
    updatedAt: '2026-01-01T00:00:00.000Z',
    ...overrides,
  };
}

export function aStoredDraft(overrides: Partial<StoredDraft> = {}): StoredDraft {
  return {
    locale: 'en',
    blocks: [],
    revision: 1,
    basedOnRevision: null,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-01T00:00:00.000Z',
    ...overrides,
  };
}

export function aTextBlock(id: string, text = 'hello'): DocumentBlock {
  return { id, kind: 'TEXT', editable: true, content: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] } };
}

/** A TEXT block whose Tiptap content embeds `blockId` — what makes a widget non-deletable. */
export function anEmbeddingBlock(id: string, blockId: string): DocumentBlock {
  return { id, kind: 'TEXT', content: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'widgetEmbed', attrs: { blockId } }] }] } };
}

export function aWidgetBlock(id: string, overrides: Partial<DocumentBlock> = {}): DocumentBlock {
  return { id, kind: 'WIDGET', placement: 'REFERENCED', type: 'entity-table', props: {}, ...overrides };
}

/** Seeds a document plus its source-locale draft, the state `createDocument` leaves behind. */
export function seedDocument(stub: DocumentStoreStub, document = aStoredDocument(), blocks: DocumentBlock[] = []): StoredDocument {
  stub.documents.set(`${document.orgKey}/${document.id}`, document);
  stub.drafts.set(`${document.orgKey}/${document.id}/${document.sourceLocale}`, aStoredDraft({ locale: document.sourceLocale, blocks }));
  return document;
}
