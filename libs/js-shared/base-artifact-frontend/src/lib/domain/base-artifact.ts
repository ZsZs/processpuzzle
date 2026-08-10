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

// Embedded, not a BaseEntity of its own: an ArtifactInputPort has no id/version/endpoint —
// it travels inside ArtifactPropertiesInput the way EMBEDDED_COMPONENTS expects.
export class ArtifactInputPort {
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

export class ArtifactOutputPort {
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
 * ArtifactBlock record: fields for the other kind are left undefined. Not edited through the
 * generic form at all in the primary flow; see ArtifactEditorComponent and ArtifactContentStore.
 * (An EMBEDDED_COMPONENTS descriptor for this shape as a secondary raw/debug view is a
 * reasonable follow-up, not sketched here.)
 */
export interface ArtifactBlock {
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

export class Artifact implements BaseEntity {
  constructor(
    public id?: string,
    public orgKey?: string,
    public title = 'Untitled artifact',
    public description?: string,
    public inputPorts: ArtifactInputPort[] = [],
    public outputPorts: ArtifactOutputPort[] = [],
    // Present on the entity because getArtifact returns it, but never written through the
    // generic BaseEntityStore save path — see ArtifactContentStore for how blocks are actually
    // persisted, and UpdateArtifactProperties for why the Properties form's PUT can't touch it.
    public blocks: ArtifactBlock[] = [],
    public version?: number,
    public createdAt?: string,
    public updatedAt?: string,
  ) {}
}
