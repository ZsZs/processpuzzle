import { BaseEntity } from '@processpuzzle/base-entity';

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

export enum WidgetPlacement {
  STANDALONE = 'STANDALONE',
  REFERENCED = 'REFERENCED',
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
 */
export interface DocumentBlock {
  id: string;
  kind: BlockKind;
  editable?: boolean;
  content?: Record<string, unknown>; // opaque Tiptap/ProseMirror JSON document
  placement?: WidgetPlacement;
  type?: string; // widget registry key
  props?: Record<string, unknown>;
  inputBindings?: Record<string, string>;
  outputBindings?: Record<string, string>;
}

export class Document implements BaseEntity {
  constructor(
    public id?: string,
    public orgKey?: string,
    public title = 'Untitled document',
    public description?: string,
    public inputPorts: DocumentInputPort[] = [],
    public outputPorts: DocumentOutputPort[] = [],
    // Present on the entity because getDocument returns it, but never written through the
    // generic BaseEntityStore save path — see DocumentContentStore for how blocks are actually
    // persisted, and UpdateDocumentProperties for why the Properties form's PUT can't touch it.
    public blocks: DocumentBlock[] = [],
    public version?: number,
    public createdAt?: string,
    public updatedAt?: string,
  ) {}
}
