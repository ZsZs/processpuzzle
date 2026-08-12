/**
 * Shapes of `base-document-api.yaml`, plus the shapes actually written to Firestore.
 *
 * The wire types are hand-written rather than generated: the Java half generates its DTOs from the
 * same contract, and having the two derive independently from one yaml is precisely what makes
 * `base-document.contract.spec.ts` meaningful — a generated client on this side would only prove the
 * generator is self-consistent.
 */

export type BlockKind = 'TEXT' | 'WIDGET';
export type WidgetPlacement = 'STANDALONE' | 'REFERENCED';
export type DocumentStatus = 'DRAFT' | 'PUBLISHED' | 'PUBLISHED_WITH_DRAFT_CHANGES';
export type PortType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'OBJECT' | 'ARRAY' | 'ENTITY_REF' | 'ENTITY_COLLECTION';

export interface AttributeVisibility {
  mode?: 'ALL' | 'INCLUDE' | 'EXCLUDE';
  attributes?: string[];
}

export interface DocumentInputPort {
  name: string;
  type: PortType;
  required?: boolean;
  description?: string | null;
  defaultValue?: unknown;
  entityType?: string | null;
  attributeVisibility?: AttributeVisibility | null;
  defaultRsqlFilter?: string | null;
}

export interface DocumentOutputPort {
  name: string;
  type: PortType;
  description?: string | null;
  entityType?: string | null;
  attributeVisibility?: AttributeVisibility | null;
}

/**
 * `content` is Tiptap/ProseMirror JSON, stored and returned verbatim. The contract declares it as
 * an object with no properties on purpose (see the `TiptapDocument` comment in the yaml), so there
 * is nothing to model here beyond "opaque JSON".
 */
export interface DocumentBlockInput {
  id?: string | null;
  kind: BlockKind;
  editable?: boolean | null;
  content?: Record<string, unknown>;
  placement?: WidgetPlacement;
  type?: string | null;
  props?: Record<string, unknown> | null;
  inputBindings?: Record<string, string> | null;
  outputBindings?: Record<string, string> | null;
}

export interface DocumentBlock extends DocumentBlockInput {
  id: string;
}

/** The twelve `DocumentPropertiesInput` fields. */
export interface DocumentPropertiesInput {
  slug: string;
  title: string;
  subject?: string | null;
  description?: string | null;
  author?: string | null;
  sourceLocale: string;
  isPublic?: boolean;
  readerRoles?: string[];
  editorRoles?: string[];
  publisherRoles?: string[];
  inputPorts?: DocumentInputPort[];
  outputPorts?: DocumentOutputPort[];
}

export interface DocumentTranslationInput {
  locale: string;
  /** `null`/absent copies the source locale's content; an explicit `[]` is a blank page. */
  blocks?: DocumentBlockInput[] | null;
}

export interface DocumentInput extends DocumentPropertiesInput {
  id?: string | null;
  translations?: DocumentTranslationInput[];
}

export interface DocumentTranslation {
  locale: string;
  blocks?: DocumentBlock[];
  status: DocumentStatus;
  revision?: number;
  publishedRevision?: number | null;
  basedOnRevision?: number | null;
  outOfDate?: boolean;
  publishedAt?: string | null;
  updatedAt?: string;
}

export interface DocumentTranslationSummary {
  locale: string;
  status: DocumentStatus;
  revision?: number;
  publishedRevision?: number | null;
  outOfDate?: boolean;
  blockCount?: number;
  publishedAt?: string | null;
  updatedAt?: string;
}

export interface DocumentSummary extends DocumentPropertiesInput {
  id: string;
  orgKey: string;
  translations?: DocumentTranslationSummary[];
  lockVersion?: number;
  createdBy?: string | null;
  createdAt?: string;
  publishedAt?: string | null;
  updatedAt?: string;
}

export interface DocumentResource extends DocumentSummary {
  /** The single translation selected by `locale`; null when that locale has none. */
  translation?: DocumentTranslation | null;
}

export interface PageOfDocumentSummary {
  content: DocumentSummary[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ErrorResponse {
  errorId: string;
  errorText: string;
}

/* ------------------------------------------------------------------ stored shapes ------------- */

/**
 * One Firestore document under `organizations/{orgKey}/documents`. Mirrors the `documents` table of
 * `base-document-backend`, except that `orgKey` lives in the path rather than in a composite key —
 * which preserves the property the Java repository relies on: an unscoped read of another tenant's
 * row is not expressible, because every accessor is built from `organizations/{orgKey}/...`.
 */
export interface StoredDocument {
  id: string;
  orgKey: string;
  slug: string;
  title: string;
  subject: string | null;
  description: string | null;
  author: string | null;
  sourceLocale: string;
  isPublic: boolean;
  readerRoles: string[];
  editorRoles: string[];
  publisherRoles: string[];
  inputPorts: DocumentInputPort[];
  outputPorts: DocumentOutputPort[];
  lockVersion: number;
  createdBy: string | null;
  createdAt: string;
  publishedAt: string | null;
  updatedAt: string;
}

/**
 * One Firestore document under `.../documents/{documentId}/drafts`, keyed by locale.
 *
 * `revision` is an editorial counter bumped by hand on every content write, deliberately distinct
 * from `lockVersion`: publishing records `publishedRevision === revision`, which a
 * write-triggered version counter would invalidate on the same flush.
 */
export interface StoredDraft {
  locale: string;
  blocks: DocumentBlock[];
  revision: number;
  basedOnRevision: number | null;
  createdAt: string;
  updatedAt: string;
}

/** One Firestore document under `.../documents/{documentId}/published`, keyed by locale. */
export interface StoredPublished {
  locale: string;
  blocks: DocumentBlock[];
  publishedRevision: number;
  publishedAt: string;
  publishedBy: string | null;
}
