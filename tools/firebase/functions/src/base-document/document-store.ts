import { getApps, initializeApp } from 'firebase-admin/app';
import { getFirestore, type CollectionReference, type DocumentReference, type Firestore } from 'firebase-admin/firestore';
import { DOCUMENTS_COLLECTION, DRAFTS_COLLECTION, ORGANIZATIONS_COLLECTION, PUBLISHED_COLLECTION } from './base-document.config.js';
import type { DocumentBlock, StoredDocument, StoredDraft, StoredPublished } from './base-document.model.js';

/** Why a block mutation could not be applied; each maps to one contract status in the handlers. */
export type BlockMutationFailure = 'not-a-permutation' | 'block-not-found' | 'block-referenced';

export type BlockMutation<T> =
  { readonly ok: true; readonly blocks: DocumentBlock[]; readonly result: T } | { readonly ok: false; readonly failure: BlockMutationFailure; readonly detail?: readonly string[] };

export type BlockMutationOutcome<T> =
  | { readonly kind: 'draft-missing' }
  | { readonly kind: 'failed'; readonly failure: BlockMutationFailure; readonly detail?: readonly string[] }
  | { readonly kind: 'applied'; readonly result: T; readonly draft: StoredDraft };

/**
 * Persistence surface the handlers see. Narrow on purpose: it is the seam the specs replace with an
 * in-memory stub, so anything Firebase-specific that leaks into it has to be stubbed too.
 */
export interface DocumentStore {
  /** Up to `limit` documents of the organization, unordered — ordering and filtering happen in memory. */
  listDocuments(orgKey: string, limit: number): Promise<StoredDocument[]>;
  findDocument(orgKey: string, documentId: string): Promise<StoredDocument | undefined>;
  findDocumentBySlug(orgKey: string, slug: string): Promise<StoredDocument | undefined>;
  saveDocument(document: StoredDocument): Promise<void>;
  /** Removes the document and every draft and published snapshot beneath it. */
  deleteDocument(orgKey: string, documentId: string): Promise<void>;
  findDraft(orgKey: string, documentId: string, locale: string): Promise<StoredDraft | undefined>;
  listDrafts(orgKey: string, documentId: string): Promise<StoredDraft[]>;
  saveDraft(orgKey: string, documentId: string, draft: StoredDraft): Promise<void>;
  deleteDraft(orgKey: string, documentId: string, locale: string): Promise<void>;
  findPublished(orgKey: string, documentId: string, locale: string): Promise<StoredPublished | undefined>;
  listPublished(orgKey: string, documentId: string): Promise<StoredPublished[]>;
  deletePublished(orgKey: string, documentId: string, locale: string): Promise<void>;
  /**
   * Applies `mutate` to a draft's block list inside a transaction and bumps its revision.
   *
   * The read-modify-write has to be atomic because every block operation is a whole-array rewrite:
   * two concurrent autosaves of different blocks would otherwise have the later write silently drop
   * the earlier one. `DocumentContentStore` debounces per block id and edits are per user, so the
   * contention window is small — but "small" is not "absent", and a lost paragraph is invisible.
   */
  mutateDraftBlocks<T>(orgKey: string, documentId: string, locale: string, mutate: (blocks: DocumentBlock[]) => BlockMutation<T>): Promise<BlockMutationOutcome<T>>;
}

/**
 * Firestore-backed `DocumentStore`, laid out as
 * `organizations/{orgKey}/documents/{documentId}/{drafts,published}/{locale}`.
 *
 * Follows `FirebaseFileStorageService`: the admin app is initialized lazily inside the accessor
 * rather than at module load, not-found is `undefined` rather than a throw so the caller can answer
 * 404, and every Firebase specific stays behind a private member.
 */
export class FirestoreDocumentStore implements DocumentStore {
  async listDocuments(orgKey: string, limit: number): Promise<StoredDocument[]> {
    const snapshot = await this.documents(orgKey).limit(limit).get();
    return snapshot.docs.map((entry) => entry.data());
  }

  async findDocument(orgKey: string, documentId: string): Promise<StoredDocument | undefined> {
    const snapshot = await this.documents(orgKey).doc(documentId).get();
    return snapshot.exists ? snapshot.data() : undefined;
  }

  async findDocumentBySlug(orgKey: string, slug: string): Promise<StoredDocument | undefined> {
    // Single-field equality, so Firestore's automatic index covers it — no composite index needed.
    const snapshot = await this.documents(orgKey).where('slug', '==', slug).limit(1).get();
    return snapshot.empty ? undefined : snapshot.docs[0].data();
  }

  async saveDocument(document: StoredDocument): Promise<void> {
    await this.documents(document.orgKey).doc(document.id).set(document);
  }

  async deleteDocument(orgKey: string, documentId: string): Promise<void> {
    const root = this.documents(orgKey).doc(documentId);
    const [published, drafts] = await Promise.all([root.collection(PUBLISHED_COLLECTION).get(), root.collection(DRAFTS_COLLECTION).get()]);

    // Content first, then the document — the same order `DeleteDocument` uses on the Java side, so a
    // failure part-way through can never leave content addressable by a document that no longer exists.
    const batch = FirestoreDocumentStore.db().batch();
    published.docs.forEach((entry) => batch.delete(entry.ref));
    drafts.docs.forEach((entry) => batch.delete(entry.ref));
    batch.delete(root);
    await batch.commit();
  }

  async findDraft(orgKey: string, documentId: string, locale: string): Promise<StoredDraft | undefined> {
    const snapshot = await this.draftRef(orgKey, documentId, locale).get();
    return snapshot.exists ? snapshot.data() : undefined;
  }

  async listDrafts(orgKey: string, documentId: string): Promise<StoredDraft[]> {
    const snapshot = await this.drafts(orgKey, documentId).get();
    return snapshot.docs.map((entry) => entry.data());
  }

  async saveDraft(orgKey: string, documentId: string, draft: StoredDraft): Promise<void> {
    await this.draftRef(orgKey, documentId, draft.locale).set(draft);
  }

  async deleteDraft(orgKey: string, documentId: string, locale: string): Promise<void> {
    await this.draftRef(orgKey, documentId, locale).delete();
  }

  async findPublished(orgKey: string, documentId: string, locale: string): Promise<StoredPublished | undefined> {
    const snapshot = await this.publishedRef(orgKey, documentId, locale).get();
    return snapshot.exists ? snapshot.data() : undefined;
  }

  async listPublished(orgKey: string, documentId: string): Promise<StoredPublished[]> {
    const snapshot = await this.published(orgKey, documentId).get();
    return snapshot.docs.map((entry) => entry.data());
  }

  async deletePublished(orgKey: string, documentId: string, locale: string): Promise<void> {
    await this.publishedRef(orgKey, documentId, locale).delete();
  }

  async mutateDraftBlocks<T>(orgKey: string, documentId: string, locale: string, mutate: (blocks: DocumentBlock[]) => BlockMutation<T>): Promise<BlockMutationOutcome<T>> {
    const ref = this.draftRef(orgKey, documentId, locale);

    return FirestoreDocumentStore.db().runTransaction<BlockMutationOutcome<T>>(async (transaction) => {
      const snapshot = await transaction.get(ref);
      if (!snapshot.exists) return { kind: 'draft-missing' };

      const draft = snapshot.data() as StoredDraft;
      const mutation = mutate(draft.blocks ?? []);
      if (!mutation.ok) return { kind: 'failed', failure: mutation.failure, detail: mutation.detail };

      const updated: StoredDraft = { ...draft, blocks: mutation.blocks, revision: draft.revision + 1, updatedAt: new Date().toISOString() };
      transaction.set(ref, updated);
      return { kind: 'applied', result: mutation.result, draft: updated };
    });
  }

  private documents(orgKey: string): CollectionReference<StoredDocument> {
    return FirestoreDocumentStore.db().collection(ORGANIZATIONS_COLLECTION).doc(orgKey).collection(DOCUMENTS_COLLECTION).withConverter(FirestoreDocumentStore.converter<StoredDocument>());
  }

  private drafts(orgKey: string, documentId: string): CollectionReference<StoredDraft> {
    return this.documents(orgKey).doc(documentId).collection(DRAFTS_COLLECTION).withConverter(FirestoreDocumentStore.converter<StoredDraft>());
  }

  private draftRef(orgKey: string, documentId: string, locale: string): DocumentReference<StoredDraft> {
    return this.drafts(orgKey, documentId).doc(locale);
  }

  private published(orgKey: string, documentId: string): CollectionReference<StoredPublished> {
    return this.documents(orgKey).doc(documentId).collection(PUBLISHED_COLLECTION).withConverter(FirestoreDocumentStore.converter<StoredPublished>());
  }

  private publishedRef(orgKey: string, documentId: string, locale: string): DocumentReference<StoredPublished> {
    return this.published(orgKey, documentId).doc(locale);
  }

  /** Identity converter — the stored shapes are already plain JSON, it only carries the type. */
  private static converter<T extends object>() {
    return {
      toFirestore: (value: T) => value,
      fromFirestore: (snapshot: { data(): Record<string, unknown> }) => snapshot.data() as T,
    };
  }

  private static db(): Firestore {
    if (!getApps().length) initializeApp();
    return getFirestore();
  }
}
