"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.FirestoreDocumentStore = void 0;
const app_1 = require("firebase-admin/app");
const firestore_1 = require("firebase-admin/firestore");
const base_document_config_js_1 = require("./base-document.config.js");
/**
 * Firestore-backed `DocumentStore`, laid out as
 * `organizations/{orgKey}/documents/{documentId}/{drafts,published}/{locale}`.
 *
 * Follows `FirebaseFileStorageService`: the admin app is initialized lazily inside the accessor
 * rather than at module load, not-found is `undefined` rather than a throw so the caller can answer
 * 404, and every Firebase specific stays behind a private member.
 */
class FirestoreDocumentStore {
    async listDocuments(orgKey, limit) {
        const snapshot = await this.documents(orgKey).limit(limit).get();
        return snapshot.docs.map((entry) => entry.data());
    }
    async findDocument(orgKey, documentId) {
        const snapshot = await this.documents(orgKey).doc(documentId).get();
        return snapshot.exists ? snapshot.data() : undefined;
    }
    async findDocumentBySlug(orgKey, slug) {
        // Single-field equality, so Firestore's automatic index covers it — no composite index needed.
        const snapshot = await this.documents(orgKey).where('slug', '==', slug).limit(1).get();
        return snapshot.empty ? undefined : snapshot.docs[0].data();
    }
    async saveDocument(document) {
        await this.documents(document.orgKey).doc(document.id).set(document);
    }
    async deleteDocument(orgKey, documentId) {
        const root = this.documents(orgKey).doc(documentId);
        const [published, drafts] = await Promise.all([root.collection(base_document_config_js_1.PUBLISHED_COLLECTION).get(), root.collection(base_document_config_js_1.DRAFTS_COLLECTION).get()]);
        // Content first, then the document — the same order `DeleteDocument` uses on the Java side, so a
        // failure part-way through can never leave content addressable by a document that no longer exists.
        const batch = FirestoreDocumentStore.db().batch();
        published.docs.forEach((entry) => batch.delete(entry.ref));
        drafts.docs.forEach((entry) => batch.delete(entry.ref));
        batch.delete(root);
        await batch.commit();
    }
    async findDraft(orgKey, documentId, locale) {
        const snapshot = await this.draftRef(orgKey, documentId, locale).get();
        return snapshot.exists ? snapshot.data() : undefined;
    }
    async listDrafts(orgKey, documentId) {
        const snapshot = await this.drafts(orgKey, documentId).get();
        return snapshot.docs.map((entry) => entry.data());
    }
    async saveDraft(orgKey, documentId, draft) {
        await this.draftRef(orgKey, documentId, draft.locale).set(draft);
    }
    async deleteDraft(orgKey, documentId, locale) {
        await this.draftRef(orgKey, documentId, locale).delete();
    }
    async findPublished(orgKey, documentId, locale) {
        const snapshot = await this.publishedRef(orgKey, documentId, locale).get();
        return snapshot.exists ? snapshot.data() : undefined;
    }
    async listPublished(orgKey, documentId) {
        const snapshot = await this.published(orgKey, documentId).get();
        return snapshot.docs.map((entry) => entry.data());
    }
    async deletePublished(orgKey, documentId, locale) {
        await this.publishedRef(orgKey, documentId, locale).delete();
    }
    async mutateDraftBlocks(orgKey, documentId, locale, mutate) {
        const ref = this.draftRef(orgKey, documentId, locale);
        return FirestoreDocumentStore.db().runTransaction(async (transaction) => {
            var _a;
            const snapshot = await transaction.get(ref);
            if (!snapshot.exists)
                return { kind: 'draft-missing' };
            const draft = snapshot.data();
            const mutation = mutate((_a = draft.blocks) !== null && _a !== void 0 ? _a : []);
            if (!mutation.ok)
                return { kind: 'failed', failure: mutation.failure, detail: mutation.detail };
            const updated = Object.assign(Object.assign({}, draft), { blocks: mutation.blocks, revision: draft.revision + 1, updatedAt: new Date().toISOString() });
            transaction.set(ref, updated);
            return { kind: 'applied', result: mutation.result, draft: updated };
        });
    }
    documents(orgKey) {
        return FirestoreDocumentStore.db().collection(base_document_config_js_1.ORGANIZATIONS_COLLECTION).doc(orgKey).collection(base_document_config_js_1.DOCUMENTS_COLLECTION).withConverter(FirestoreDocumentStore.converter());
    }
    drafts(orgKey, documentId) {
        return this.documents(orgKey).doc(documentId).collection(base_document_config_js_1.DRAFTS_COLLECTION).withConverter(FirestoreDocumentStore.converter());
    }
    draftRef(orgKey, documentId, locale) {
        return this.drafts(orgKey, documentId).doc(locale);
    }
    published(orgKey, documentId) {
        return this.documents(orgKey).doc(documentId).collection(base_document_config_js_1.PUBLISHED_COLLECTION).withConverter(FirestoreDocumentStore.converter());
    }
    publishedRef(orgKey, documentId, locale) {
        return this.published(orgKey, documentId).doc(locale);
    }
    /** Identity converter — the stored shapes are already plain JSON, it only carries the type. */
    static converter() {
        return {
            toFirestore: (value) => value,
            fromFirestore: (snapshot) => snapshot.data(),
        };
    }
    static db() {
        if (!(0, app_1.getApps)().length)
            (0, app_1.initializeApp)();
        return (0, firestore_1.getFirestore)();
    }
}
exports.FirestoreDocumentStore = FirestoreDocumentStore;
//# sourceMappingURL=document-store.js.map