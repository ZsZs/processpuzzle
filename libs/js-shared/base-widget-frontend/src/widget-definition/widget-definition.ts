import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of `base-widget-api.yaml`'s `WidgetDefinition` — the description of a widget
 * *type*, as opposed to {@link WidgetInstance}, which is one placement of a type inside a container.
 * The join is `WidgetInstance.type === WidgetDefinition.key`.
 */

export const PORT_TYPES = ['STRING', 'NUMBER', 'BOOLEAN', 'DATE', 'OBJECT', 'ARRAY', 'ENTITY_REF', 'ENTITY_COLLECTION'] as const;
export type PortType = (typeof PORT_TYPES)[number];

export const ATTRIBUTE_VISIBILITY_MODES = ['ALL', 'INCLUDE', 'EXCLUDE'] as const;
export type AttributeVisibilityMode = (typeof ATTRIBUTE_VISIBILITY_MODES)[number];

export const WIDGET_DEFINITION_STATUSES = ['DRAFT', 'PUBLISHED'] as const;
export type WidgetDefinitionStatus = (typeof WIDGET_DEFINITION_STATUSES)[number];

export interface AttributeVisibility {
  mode: AttributeVisibilityMode;
  attributes: string[];
}

/** A value the container supplies to the widget. `name` is what a binding references. */
export interface InputPort {
  name: string;
  type: PortType;
  required?: boolean;
  description?: string;
  defaultValue?: unknown;
  entityType?: string;
  attributeVisibility?: AttributeVisibility;
  defaultRsqlFilter?: string;
}

/** The reverse of {@link InputPort}: a value the widget emits back to its container. */
export interface OutputPort {
  name: string;
  type: PortType;
  description?: string;
  entityType?: string;
  attributeVisibility?: AttributeVisibility;
}

/**
 * A JSON Schema describing one widget type's props. Deliberately typed loosely: this is arbitrary
 * JSON Schema authored per widget, and {@link propsSchemaToDescriptors} reads only the subset it
 * understands rather than asserting a shape the contract does not enforce.
 */
export interface PropsSchema {
  type?: string;
  properties?: Record<string, PropsSchemaProperty>;
  required?: string[];
  [key: string]: unknown;
}

export interface PropsSchemaProperty {
  type?: string;
  title?: string;
  description?: string;
  enum?: string[];
  format?: string;
  maxLength?: number;
  default?: unknown;
  items?: PropsSchemaProperty;
  [key: string]: unknown;
}

export class WidgetDefinition implements BaseEntity {
  /**
   * Declared, never assigned. The contract gives a definition no `id` — `key` identifies it — but
   * `BaseEntity`'s only property is an optional `id`, and TypeScript's weak-type rule rejects a type
   * sharing no property with it. `declare` emits nothing, so the payload stays exactly the schema's
   * shape. Same device as `RegionDefinition`.
   */
  declare readonly id?: string;

  /** Registry key; immutable, because every stored `WidgetInstance.type` references it. */
  key: string;
  name: string;
  translocoId?: string;
  description?: string;
  category?: string;
  icon?: string;
  /**
   * Undefined means "props are unconstrained" — the honest state for a widget type nobody has
   * described yet. Distinct from an empty schema, which asserts the widget takes no props at all;
   * the contract keeps the field nullable for exactly this reason.
   */
  propsSchema?: PropsSchema;
  inputPorts?: InputPort[];
  outputPorts?: OutputPort[];
  // region server-assigned
  orgKey?: string;
  status?: WidgetDefinitionStatus;
  version?: number;
  publishedVersion?: number;
  createdAt?: string;
  updatedAt?: string;
  // endregion

  constructor(init: Partial<WidgetDefinition> = {}) {
    this.key = init.key ?? '';
    this.name = init.name ?? '';
    this.translocoId = init.translocoId;
    this.description = init.description;
    this.category = init.category;
    this.icon = init.icon;
    this.propsSchema = init.propsSchema;
    this.inputPorts = init.inputPorts;
    this.outputPorts = init.outputPorts;
    this.orgKey = init.orgKey;
    this.status = init.status;
    this.version = init.version;
    this.publishedVersion = init.publishedVersion;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
