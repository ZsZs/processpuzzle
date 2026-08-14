import { BaseEntity } from '@processpuzzle/base-entity';
import { WidgetInstance } from '@processpuzzle/base-widget';

// Re-exported rather than redeclared: WidgetPlacement is one enum shared with base-app, and it now
// has a single declaration next to WIDGET_REGISTRY. The const-object form means existing
// `WidgetPlacement.STANDALONE` call sites keep working unchanged.
export { WIDGET_PLACEMENTS, WidgetPlacement } from '@processpuzzle/base-widget';

export enum PortType {
  STRING = 'STRING',
  NUMBER = 'NUMBER',
  BOOLEAN = 'BOOLEAN',
  DATE = 'DATE',
  OBJECT = 'OBJECT',
  ARRAY = 'ARRAY',
  ENTITY_REF = 'ENTITY_REF',
  ENTITY_COLLECTION = 'ENTITY_COLLECTION',
}

export enum AttributeVisibilityMode {
  ALL = 'ALL',
  INCLUDE = 'INCLUDE',
  EXCLUDE = 'EXCLUDE',
}

export interface AttributeVisibility {
  mode: AttributeVisibilityMode;
  attributes: string[];
}

export enum BlockKind {
  TEXT = 'TEXT',
  WIDGET = 'WIDGET',
}

// Embedded, not an aggregate root of its own: a DocumentInputPort has no id/version/endpoint — it travels
// inside DocumentPropertiesInput the way EMBEDDED_COMPONENTS expects.
export class DocumentInputPort implements BaseEntity {
  /**
   * Declared, never assigned. The contract gives a port no `id` — `name` identifies it, see
   * `DOCUMENT_PORT_ID_FIELD` — but `BaseEntity`'s only property is an optional `id`, and TypeScript's
   * weak-type rule rejects a type that shares no property with it. `declare` emits nothing, so the payload
   * stays exactly the shape the schema describes.
   */
  declare readonly id?: string;

  constructor(
    public name = '',
    public type: PortType = PortType.STRING,
    public required = false,
    public description?: string,
    public defaultValue?: unknown,
    public entityType?: string,
    public attributeVisibility?: AttributeVisibility,
    public defaultRsqlFilter?: string,
  ) {}
}

/** The output side of {@link DocumentInputPort}, embedded for the same reason and identified the same way. */
export class DocumentOutputPort implements BaseEntity {
  declare readonly id?: string;

  constructor(
    public name = '',
    public type: PortType = PortType.STRING,
    public description?: string,
    public entityType?: string,
    public attributeVisibility?: AttributeVisibility,
  ) {}
}

/**
 * The full block shape, TEXT and WIDGET fields side by side — same rationale as the backend's
 * DocumentBlock record: fields for the other kind are left undefined. Not edited through the
 * generic form at all in the primary flow; see DocumentEditorComponent and DocumentContentStore.
 * (An EMBEDDED_COMPONENTS descriptor for this shape as a secondary raw/debug view is a
 * reasonable follow-up, not sketched here.)
 *
 * The WIDGET half comes from {@link WidgetInstance} rather than being restated here, so the two
 * cannot drift the way the old local copy had already begun to — it was missing nothing yet, but
 * base-app's equivalent had no bindings at all. `Partial` because a block carries the widget
 * fields only when `kind` is WIDGET: a TEXT block has no `type`, which a WidgetInstance requires.
 * That requiredness mismatch is also why `base-document-api.yaml` keeps its own flattened copy of
 * the schema instead of mapping onto the generated shared DTO — see the note on
 * `DocumentBlockInput` there.
 */
export interface DocumentBlock extends Partial<WidgetInstance> {
  id: string;
  kind: BlockKind;
  editable?: boolean;
  content?: Record<string, unknown>; // opaque Tiptap/ProseMirror JSON document
}

export enum DocumentStatus {
  DRAFT = 'DRAFT',
  PUBLISHED = 'PUBLISHED',
  PUBLISHED_WITH_DRAFT_CHANGES = 'PUBLISHED_WITH_DRAFT_CHANGES',
}

/**
 * One locale's block list plus its publication state — the unit every block operation is scoped to.
 * `revision` is an editorial counter over content edits, not an optimistic-locking token; the document's
 * own lock version is a separate field, see the contract's note on `Document.lockVersion`.
 */
export interface DocumentTranslation {
  locale: string;
  blocks: DocumentBlock[];
  status: DocumentStatus;
  revision?: number;
  publishedRevision?: number | null;
  basedOnRevision?: number | null;
}

/** A translation as it appears in `Document.translations`: the state of a locale, without its blocks. */
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

/**
 * The locales a document may declare as its source. A closed list rather than free text on the form, because
 * `Locale`'s BCP-47 pattern admits far more than an application ships translations for, and the ones it does
 * ship are the honest choice — the same five `LANGUAGE_CONFIGURATION` offers.
 *
 * Key and value are both the bare tag on purpose: what the picker shows is what the contract stores, so a
 * document's source locale reads the same on the form as in the payload and in the editor's locale tab.
 */
export const DOCUMENT_SOURCE_LOCALES = ['en', 'de', 'es', 'fr', 'hu'] as const;

/**
 * `DocumentSlug`'s pattern, verbatim from the contract. Carried here rather than inlined in the descriptor so
 * that the form validator and any client-side slug handling cannot drift from each other.
 */
export const DOCUMENT_SLUG_PATTERN = '^[a-z0-9]+(-[a-z0-9]+)*$';

/**
 * Every language-invariant field of `DocumentPropertiesInput`, in the contract's own order.
 *
 * All twelve, not the four the generic form used to carry: `PUT /documents/{id}/properties` *replaces* the
 * properties block, so a body short of a field blanks it — and `slug` and `sourceLocale` are required, so a
 * body short of those is a 400 rather than a quiet loss. The document's content is deliberately absent; it
 * hangs off {@link translations} and is edited through the block endpoints. See BaseDocumentService.
 */
export class Document implements BaseEntity {
  constructor(
    public id?: string,
    public orgKey?: string,
    /** URL-safe route key, unique within the organization and invariant across locales. Required by the contract. */
    public slug = '',
    public title = 'Untitled document',
    /** What the document is about, one line — distinct from {@link description}, which summarizes it. */
    public subject?: string,
    public description?: string,
    /** Editable byline. Defaults to `createdBy` server-side, and never overwrites it. */
    public author?: string,
    /** The locale the document is authored in; every other translation is a translation *of* this one. Required. */
    public sourceLocale = 'en',
    /** When true the published content is readable without authentication, and {@link readerRoles} is irrelevant. */
    public isPublic = false,
    /** Empty means any authenticated member of the organization — base-app's NavNode.roles convention. */
    public readerRoles: string[] = [],
    public editorRoles: string[] = [],
    /** Empty falls back to {@link editorRoles}: publishing is a distinct authority, but need not be separated. */
    public publisherRoles: string[] = [],
    public inputPorts: DocumentInputPort[] = [],
    public outputPorts: DocumentOutputPort[] = [],
    /**
     * Publication state of every locale, without block content — what a locale picker would list.
     * Deliberately *not* a `blocks` field: the contract gives the document no root block list, and
     * `listDocuments` returns summaries with no content at all, so an entity loaded through the generic
     * store never carries blocks. The content editor fetches the translation it edits itself; see
     * DocumentContentTabComponent and BaseDocumentService.getTranslation.
     */
    public translations: DocumentTranslationSummary[] = [],
    /** The one locale `getDocument` was asked for, when the entity came from that call rather than the list. */
    public translation?: DocumentTranslation,
    public version?: number,
    public createdAt?: string,
    public updatedAt?: string,
  ) {}
}

/**
 * `DocumentPropertiesInput` as a type: exactly the fields `PUT /documents/{id}/properties` accepts, derived
 * from {@link Document} so that a field added to one is a compile error in whatever builds the other.
 *
 * A `Pick` and not an interface of its own — the point is that the two cannot diverge, and that the content
 * fields are excluded by construction rather than by remembering to leave them out.
 */
export type DocumentProperties = Pick<
  Document,
  'slug' | 'title' | 'subject' | 'description' | 'author' | 'sourceLocale' | 'isPublic' | 'readerRoles' | 'editorRoles' | 'publisherRoles' | 'inputPorts' | 'outputPorts'
>;
