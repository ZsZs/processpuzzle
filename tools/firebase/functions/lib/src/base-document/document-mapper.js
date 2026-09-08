"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.nowIso = nowIso;
exports.toStoredProperties = toStoredProperties;
exports.toNewStoredDocument = toNewStoredDocument;
exports.withProperties = withProperties;
exports.newDraft = newDraft;
exports.toBlock = toBlock;
exports.toBlocks = toBlocks;
exports.deriveStatus = deriveStatus;
exports.isOutOfDate = isOutOfDate;
exports.toTranslation = toTranslation;
exports.toTranslationSummary = toTranslationSummary;
exports.toTranslationSummaries = toTranslationSummaries;
exports.toDocumentSummary = toDocumentSummary;
exports.toDocumentResource = toDocumentResource;
const node_crypto_1 = require("node:crypto");
/**
 * Wire ⇄ stored translation for `base-document`.
 *
 * Two responsibilities are load-bearing rather than incidental:
 *
 * 1. **Whitelisting.** `toStoredProperties` copies the twelve `DocumentPropertiesInput` fields by
 *    name and nothing else. The frontend's `toDto` is a naked spread of its entity
 *    (`base-document.mapper.ts:48-50`), so a create body also carries `translations`, `translation`
 *    and `version`; persisting those would put a stale copy of the content next to the drafts that
 *    actually own it.
 * 2. **No `undefined`.** Firestore rejects `undefined` field values, so absent optionals become
 *    `null` on documents and are dropped entirely from blocks (whose shape is the caller's, not ours).
 */
function nowIso() {
    return new Date().toISOString();
}
/** The twelve contract fields, defaulted as the schema declares. Unknown keys are dropped. */
function toStoredProperties(input) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j;
    return {
        slug: input.slug,
        title: input.title,
        subject: (_a = input.subject) !== null && _a !== void 0 ? _a : null,
        description: (_b = input.description) !== null && _b !== void 0 ? _b : null,
        author: (_c = input.author) !== null && _c !== void 0 ? _c : null,
        sourceLocale: input.sourceLocale,
        isPublic: (_d = input.isPublic) !== null && _d !== void 0 ? _d : false,
        readerRoles: (_e = input.readerRoles) !== null && _e !== void 0 ? _e : [],
        editorRoles: (_f = input.editorRoles) !== null && _f !== void 0 ? _f : [],
        publisherRoles: (_g = input.publisherRoles) !== null && _g !== void 0 ? _g : [],
        inputPorts: (_h = input.inputPorts) !== null && _h !== void 0 ? _h : [],
        outputPorts: (_j = input.outputPorts) !== null && _j !== void 0 ? _j : [],
    };
}
/**
 * A freshly created document. The id is minted here and any `id` in the payload is ignored — the
 * same choice `CreateDocument` makes on the Java side, so a client cannot pick its own primary key.
 */
function toNewStoredDocument(orgKey, input, createdBy, timestamp = nowIso()) {
    return Object.assign(Object.assign({}, toStoredProperties(input)), { id: (0, node_crypto_1.randomUUID)(), orgKey, lockVersion: 0, createdBy, createdAt: timestamp, publishedAt: null, updatedAt: timestamp });
}
/**
 * Existing document with new properties. `createdAt`/`createdBy` are immutable and `publishedAt` is
 * owned by the publishing operations, so all three survive the update untouched.
 */
function withProperties(existing, input, timestamp = nowIso()) {
    return Object.assign(Object.assign(Object.assign({}, existing), toStoredProperties(input)), { lockVersion: existing.lockVersion + 1, updatedAt: timestamp });
}
function newDraft(locale, blocks, basedOnRevision, timestamp = nowIso()) {
    return { locale, blocks, revision: 1, basedOnRevision, createdAt: timestamp, updatedAt: timestamp };
}
/**
 * A block with a server-assigned id. `input.id` is deliberately ignored: `appendDocumentBlock`
 * mints one and `replaceDocumentBlock` takes it from the path, so a body id never decides identity.
 */
function toBlock(id, input) {
    const block = { id, kind: input.kind };
    if (input.editable !== undefined && input.editable !== null)
        block.editable = input.editable;
    if (input.content !== undefined)
        block.content = input.content;
    if (input.placement !== undefined)
        block.placement = input.placement;
    if (input.type !== undefined && input.type !== null)
        block.type = input.type;
    if (input.props !== undefined && input.props !== null)
        block.props = input.props;
    if (input.inputBindings !== undefined && input.inputBindings !== null)
        block.inputBindings = input.inputBindings;
    if (input.outputBindings !== undefined && input.outputBindings !== null)
        block.outputBindings = input.outputBindings;
    return block;
}
function toBlocks(inputs) {
    return inputs.map((input) => { var _a; return toBlock((_a = input.id) !== null && _a !== void 0 ? _a : (0, node_crypto_1.randomUUID)(), input); });
}
/**
 * `DocumentStatus` is derived, never stored — the contract says so explicitly, and storing it would
 * create a second source of truth that publishing has to keep in step.
 */
function deriveStatus(draft, published) {
    if (!published)
        return 'DRAFT';
    if (!draft || draft.revision === published.publishedRevision)
        return 'PUBLISHED';
    return 'PUBLISHED_WITH_DRAFT_CHANGES';
}
/**
 * A translation is out of date when it was branched from a source revision the source has since
 * moved past. The source locale is never out of date with respect to itself.
 */
function isOutOfDate(draft, sourceLocale, sourceDraft) {
    if (draft.locale === sourceLocale)
        return false;
    if (draft.basedOnRevision === null || sourceDraft === undefined)
        return false;
    return draft.basedOnRevision < sourceDraft.revision;
}
function toTranslation(draft, published, sourceLocale, sourceDraft) {
    var _a, _b, _c;
    return {
        locale: draft.locale,
        blocks: (_a = draft.blocks) !== null && _a !== void 0 ? _a : [],
        status: deriveStatus(draft, published),
        revision: draft.revision,
        publishedRevision: (_b = published === null || published === void 0 ? void 0 : published.publishedRevision) !== null && _b !== void 0 ? _b : null,
        basedOnRevision: draft.basedOnRevision,
        outOfDate: isOutOfDate(draft, sourceLocale, sourceDraft),
        publishedAt: (_c = published === null || published === void 0 ? void 0 : published.publishedAt) !== null && _c !== void 0 ? _c : null,
        updatedAt: draft.updatedAt,
    };
}
function toTranslationSummary(draft, published, sourceLocale, sourceDraft) {
    var _a, _b, _c;
    return {
        locale: draft.locale,
        status: deriveStatus(draft, published),
        revision: draft.revision,
        publishedRevision: (_a = published === null || published === void 0 ? void 0 : published.publishedRevision) !== null && _a !== void 0 ? _a : null,
        outOfDate: isOutOfDate(draft, sourceLocale, sourceDraft),
        blockCount: ((_b = draft.blocks) !== null && _b !== void 0 ? _b : []).length,
        publishedAt: (_c = published === null || published === void 0 ? void 0 : published.publishedAt) !== null && _c !== void 0 ? _c : null,
        updatedAt: draft.updatedAt,
    };
}
/** Source locale first, then alphabetical — the order `DocumentTranslationAssembler` produces. */
function toTranslationSummaries(document, drafts, published) {
    const sourceDraft = drafts.find((draft) => draft.locale === document.sourceLocale);
    const publishedByLocale = new Map(published.map((snapshot) => [snapshot.locale, snapshot]));
    return [...drafts]
        .sort((left, right) => {
        if (left.locale === document.sourceLocale)
            return -1;
        if (right.locale === document.sourceLocale)
            return 1;
        return left.locale.localeCompare(right.locale);
    })
        .map((draft) => toTranslationSummary(draft, publishedByLocale.get(draft.locale), document.sourceLocale, sourceDraft));
}
function toDocumentSummary(document, translations) {
    return {
        id: document.id,
        orgKey: document.orgKey,
        slug: document.slug,
        title: document.title,
        subject: document.subject,
        description: document.description,
        author: document.author,
        sourceLocale: document.sourceLocale,
        isPublic: document.isPublic,
        readerRoles: document.readerRoles,
        editorRoles: document.editorRoles,
        publisherRoles: document.publisherRoles,
        inputPorts: document.inputPorts,
        outputPorts: document.outputPorts,
        translations,
        lockVersion: document.lockVersion,
        createdBy: document.createdBy,
        createdAt: document.createdAt,
        publishedAt: document.publishedAt,
        updatedAt: document.updatedAt,
    };
}
function toDocumentResource(document, translations, translation) {
    return Object.assign(Object.assign({}, toDocumentSummary(document, translations)), { translation });
}
//# sourceMappingURL=document-mapper.js.map