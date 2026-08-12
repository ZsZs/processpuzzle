import { randomUUID } from 'node:crypto';
import type {
  DocumentBlock,
  DocumentBlockInput,
  DocumentInput,
  DocumentPropertiesInput,
  DocumentResource,
  DocumentStatus,
  DocumentSummary,
  DocumentTranslation,
  DocumentTranslationSummary,
  StoredDocument,
  StoredDraft,
  StoredPublished,
} from './base-document.model.js';

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

export function nowIso(): string {
  return new Date().toISOString();
}

/** The twelve properties as stored: no `undefined`, defaults applied, unknown keys dropped. */
export type StoredProperties = Pick<
  StoredDocument,
  'slug' | 'title' | 'subject' | 'description' | 'author' | 'sourceLocale' | 'isPublic' | 'readerRoles' | 'editorRoles' | 'publisherRoles' | 'inputPorts' | 'outputPorts'
>;

/** The twelve contract fields, defaulted as the schema declares. Unknown keys are dropped. */
export function toStoredProperties(input: DocumentPropertiesInput): StoredProperties {
  return {
    slug: input.slug,
    title: input.title,
    subject: input.subject ?? null,
    description: input.description ?? null,
    author: input.author ?? null,
    sourceLocale: input.sourceLocale,
    isPublic: input.isPublic ?? false,
    readerRoles: input.readerRoles ?? [],
    editorRoles: input.editorRoles ?? [],
    publisherRoles: input.publisherRoles ?? [],
    inputPorts: input.inputPorts ?? [],
    outputPorts: input.outputPorts ?? [],
  };
}

/**
 * A freshly created document. The id is minted here and any `id` in the payload is ignored — the
 * same choice `CreateDocument` makes on the Java side, so a client cannot pick its own primary key.
 */
export function toNewStoredDocument(orgKey: string, input: DocumentInput, createdBy: string | null, timestamp: string = nowIso()): StoredDocument {
  return {
    ...toStoredProperties(input),
    id: randomUUID(),
    orgKey,
    lockVersion: 0,
    createdBy,
    createdAt: timestamp,
    publishedAt: null,
    updatedAt: timestamp,
  };
}

/**
 * Existing document with new properties. `createdAt`/`createdBy` are immutable and `publishedAt` is
 * owned by the publishing operations, so all three survive the update untouched.
 */
export function withProperties(existing: StoredDocument, input: DocumentPropertiesInput, timestamp: string = nowIso()): StoredDocument {
  return {
    ...existing,
    ...toStoredProperties(input),
    lockVersion: existing.lockVersion + 1,
    updatedAt: timestamp,
  };
}

export function newDraft(locale: string, blocks: DocumentBlock[], basedOnRevision: number | null, timestamp: string = nowIso()): StoredDraft {
  return { locale, blocks, revision: 1, basedOnRevision, createdAt: timestamp, updatedAt: timestamp };
}

/**
 * A block with a server-assigned id. `input.id` is deliberately ignored: `appendDocumentBlock`
 * mints one and `replaceDocumentBlock` takes it from the path, so a body id never decides identity.
 */
export function toBlock(id: string, input: DocumentBlockInput): DocumentBlock {
  const block: DocumentBlock = { id, kind: input.kind };
  if (input.editable !== undefined && input.editable !== null) block.editable = input.editable;
  if (input.content !== undefined) block.content = input.content;
  if (input.placement !== undefined) block.placement = input.placement;
  if (input.type !== undefined && input.type !== null) block.type = input.type;
  if (input.props !== undefined && input.props !== null) block.props = input.props;
  if (input.inputBindings !== undefined && input.inputBindings !== null) block.inputBindings = input.inputBindings;
  if (input.outputBindings !== undefined && input.outputBindings !== null) block.outputBindings = input.outputBindings;
  return block;
}

export function toBlocks(inputs: readonly DocumentBlockInput[]): DocumentBlock[] {
  return inputs.map((input) => toBlock(input.id ?? randomUUID(), input));
}

/**
 * `DocumentStatus` is derived, never stored — the contract says so explicitly, and storing it would
 * create a second source of truth that publishing has to keep in step.
 */
export function deriveStatus(draft: StoredDraft | undefined, published: StoredPublished | undefined): DocumentStatus {
  if (!published) return 'DRAFT';
  if (!draft || draft.revision === published.publishedRevision) return 'PUBLISHED';
  return 'PUBLISHED_WITH_DRAFT_CHANGES';
}

/**
 * A translation is out of date when it was branched from a source revision the source has since
 * moved past. The source locale is never out of date with respect to itself.
 */
export function isOutOfDate(draft: StoredDraft, sourceLocale: string, sourceDraft: StoredDraft | undefined): boolean {
  if (draft.locale === sourceLocale) return false;
  if (draft.basedOnRevision === null || sourceDraft === undefined) return false;
  return draft.basedOnRevision < sourceDraft.revision;
}

export function toTranslation(draft: StoredDraft, published: StoredPublished | undefined, sourceLocale: string, sourceDraft: StoredDraft | undefined): DocumentTranslation {
  return {
    locale: draft.locale,
    blocks: draft.blocks ?? [],
    status: deriveStatus(draft, published),
    revision: draft.revision,
    publishedRevision: published?.publishedRevision ?? null,
    basedOnRevision: draft.basedOnRevision,
    outOfDate: isOutOfDate(draft, sourceLocale, sourceDraft),
    publishedAt: published?.publishedAt ?? null,
    updatedAt: draft.updatedAt,
  };
}

export function toTranslationSummary(draft: StoredDraft, published: StoredPublished | undefined, sourceLocale: string, sourceDraft: StoredDraft | undefined): DocumentTranslationSummary {
  return {
    locale: draft.locale,
    status: deriveStatus(draft, published),
    revision: draft.revision,
    publishedRevision: published?.publishedRevision ?? null,
    outOfDate: isOutOfDate(draft, sourceLocale, sourceDraft),
    blockCount: (draft.blocks ?? []).length,
    publishedAt: published?.publishedAt ?? null,
    updatedAt: draft.updatedAt,
  };
}

/** Source locale first, then alphabetical — the order `DocumentTranslationAssembler` produces. */
export function toTranslationSummaries(document: StoredDocument, drafts: readonly StoredDraft[], published: readonly StoredPublished[]): DocumentTranslationSummary[] {
  const sourceDraft = drafts.find((draft) => draft.locale === document.sourceLocale);
  const publishedByLocale = new Map(published.map((snapshot) => [snapshot.locale, snapshot]));

  return [...drafts]
    .sort((left, right) => {
      if (left.locale === document.sourceLocale) return -1;
      if (right.locale === document.sourceLocale) return 1;
      return left.locale.localeCompare(right.locale);
    })
    .map((draft) => toTranslationSummary(draft, publishedByLocale.get(draft.locale), document.sourceLocale, sourceDraft));
}

export function toDocumentSummary(document: StoredDocument, translations: DocumentTranslationSummary[]): DocumentSummary {
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

export function toDocumentResource(document: StoredDocument, translations: DocumentTranslationSummary[], translation: DocumentTranslation | null): DocumentResource {
  return { ...toDocumentSummary(document, translations), translation };
}
