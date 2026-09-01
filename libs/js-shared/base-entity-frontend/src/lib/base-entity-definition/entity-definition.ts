import type { BaseEntity } from '../base-entity/base-entity';

/**
 * The knowledge layer of `base-entity-api.yaml` as the frontend reads it: what an entity *type* is,
 * declared as data rather than as a TypeScript class with a `BaseEntityFacade` beside it.
 *
 * The model is read by two surfaces and it is deliberately one model for both. At **run time** a
 * definition arrives as JSON and is turned into a `BaseEntityDescriptor` by
 * `dynamic-entity.descriptor.ts`, so the descriptor is the thing the framework already knows how to
 * render. In the **designer** the very same rows are the entity being edited — see
 * `base-entity-authoring/` — which is what keeps the modelling tool and the runtime from drifting apart.
 *
 * Classes rather than interfaces, and only because of that second surface: `BaseEntityStore.createEntity()`
 * mints the blank row an `Add` opens a form on, and that needs a constructor. They stay plain data — a
 * definition read by `EntityDefinitionService` is the parsed JSON, never an instance of these classes, so
 * nothing may rely on `instanceof` or on a method.
 *
 * Field names mirror the contract exactly. Where the contract and the frontend disagree — its
 * `formControlType` enum is a superset of `FormControlType`, its `code`s are what the descriptor calls
 * entity *names* — the translation is the descriptor factory's job and is documented there.
 */

/** `EntityDefinitionStatus` of the contract. Only `ACTIVE` definitions are rendered. */
export type EntityDefinitionStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED';

/** `ValueKind` of the contract: what the attribute's value *is*, as opposed to how it is edited. */
export type EntityValueKind = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'DATE_TIME' | 'ENUM' | 'REFERENCE';

/**
 * The three enums of the contract, as the authoring form's dropdown options.
 *
 * Spelled out next to the types they enumerate rather than derived from anything: a TypeScript union has
 * no run-time representation, so the list and the type have to be written twice — declaring them side by
 * side is what makes a divergence visible in one diff.
 *
 * {@link ENTITY_FORM_CONTROL_TYPES} is the **contract's** `FormControlType`, which is what the backend
 * validates a definition against — not the `FormControlType` of `abstact-attr.descriptor.ts`, whose extra
 * members (`ADDITIONAL_PROPERTIES`, `FLEX_BOX`, `LABEL`, `TAGS`, `TITLE`) are rendering concerns a
 * definition cannot name. `CONTROL_TYPES` in `dynamic-entity.descriptor.ts` maps one onto the other.
 */
export const ENTITY_DEFINITION_STATUSES: readonly EntityDefinitionStatus[] = ['DRAFT', 'ACTIVE', 'DEPRECATED'];
export const ENTITY_VALUE_KINDS: readonly EntityValueKind[] = ['TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATE_TIME', 'ENUM', 'REFERENCE'];
export const ENTITY_FORM_CONTROL_TYPES: readonly string[] = [
  'TEXT',
  'TEXTAREA',
  'TEXT_BOX',
  'NUMBER',
  'DATE',
  'DATE_TIME',
  'BOOLEAN',
  'CHECKBOX',
  'RADIO',
  'DROPDOWN',
  'ENUM_SELECT',
  'FOREIGN_KEY',
  'LOOKUP',
  'EMBEDDED_COMPONENTS',
  'COMPONENTS',
  'RELATED_ENTITIES',
  'ARTIFACT',
];

/**
 * One attribute of a definition — the contract's `BaseEntityAttribute`.
 *
 * `formControlType` is a plain string rather than the frontend's `FormControlType` enum: the contract
 * declares control types the frontend has no component for (`TEXT`, `NUMBER`, `ENUM_SELECT`, …), and
 * narrowing here would push the mapping into a cast at the parse boundary, where there is nothing
 * sensible to do about a value that does not fit.
 */
export class EntityAttributeDefinition implements BaseEntity {
  /**
   * Declared, never assigned. The contract does give an attribute a read-only `id`, but nothing addresses
   * one by it: `code` is unique within the definition, it is the path segment of
   * `PUT .../attributes/{attributeCode}`, and it is what `ENTITY_ATTRIBUTE_ID_FIELD` points the embedded
   * list at. `BaseEntity`'s only property is an optional `id` and TypeScript's weak-type rule rejects a
   * type sharing no property with it, so the field is declared to satisfy that — `declare` emits nothing,
   * and the payload stays exactly the shape `BaseEntityAttributeInput` describes.
   */
  declare readonly id?: string;

  code: string;
  name?: string;
  /**
   * Not part of the contract's `BaseEntityAttributeInput`, and therefore not authorable: the attribute
   * form offers no control for it, because a value typed into one would be dropped by the next save. Kept
   * on the model so that a backend which gains the field round-trips it untouched.
   */
  description?: string;
  displayOrder?: number;
  valueKind?: EntityValueKind;
  formControlType: string;
  isMultiValued?: boolean;
  required?: boolean;
  indexed?: boolean;
  defaultValue?: unknown;
  /** Only meaningful for `valueKind: ENUM`; becomes the dropdown's options. */
  enumValues?: string[];
  /**
   * **Code** of the definition this attribute points at — a child carried inline
   * (`EMBEDDED_COMPONENTS`) or a parent referenced by key (`FOREIGN_KEY`). The descriptor's
   * `linkedEntityType` holds the entity *name* of the same definition.
   */
  linkedEntityType?: string;
  /** Marks the attribute that titles an instance — the descriptor's `isLinkToDetails`. */
  isLinkToDetails?: boolean;

  constructor(init: Partial<EntityAttributeDefinition> = {}) {
    this.code = init.code ?? '';
    this.name = init.name;
    this.description = init.description;
    this.displayOrder = init.displayOrder;
    this.valueKind = init.valueKind;
    // The contract requires it and the form marks it required too, but a blank row has to exist before it
    // can be filled in — hence a default rather than a throw.
    this.formControlType = init.formControlType ?? 'TEXT_BOX';
    this.isMultiValued = init.isMultiValued;
    this.required = init.required;
    this.indexed = init.indexed;
    this.defaultValue = init.defaultValue;
    this.enumValues = init.enumValues;
    this.linkedEntityType = init.linkedEntityType;
    this.isLinkToDetails = init.isLinkToDetails;
  }
}

/**
 * An entity type — the contract's `BaseEntityDefinition`.
 *
 * {@link id} is a **mirror of {@link code}** rather than an independent field, maintained by
 * `EntityDefinitionMapper`. The contract addresses a definition by `code`
 * (`/entity-definitions/{code}`) while the generated screens address a record by `id`: every
 * single-record URL `BaseEntityRestService` builds comes from `id`, and so does the details link
 * `BaseFormNavigatorSingletonStore` renders. Mirroring the two in the mapper is what lets the stock
 * screens author definitions unchanged — the same arrangement `StateMachineDefinition` uses for its
 * `entityName`. The read-only uuid the contract also calls `id` is not carried: nothing addresses a
 * definition by it, and `BaseEntityDefinitionInput` has no field to send it back in.
 */
export class EntityDefinition implements BaseEntity {
  /** Mirror of {@link code}; maintained by the mapper and never edited. See the class comment. */
  id?: string;
  code: string;
  name: string;
  description?: string;
  status?: EntityDefinitionStatus;
  /** **Codes** of the definitions that may aggregate this one; empty for a stand-alone entity. */
  componentParents?: string[];
  /** True when this definition's payload travels inside its parent's and it has no rows of its own. */
  isEmbedded?: boolean;
  attributes?: EntityAttributeDefinition[];
  // region server-assigned
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  // endregion

  constructor(init: Partial<EntityDefinition> = {}) {
    this.code = init.code ?? '';
    this.id = init.id ?? this.code;
    this.name = init.name ?? '';
    this.description = init.description;
    this.status = init.status;
    // Empty arrays rather than undefined, so the TAGS control and the embedded list always have something
    // to append to.
    this.componentParents = init.componentParents ?? [];
    this.isEmbedded = init.isEmbedded ?? false;
    this.attributes = init.attributes ?? [];
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}

/**
 * An instance of a metadata-defined entity, as the framework's components see it: **flat**.
 *
 * The contract keeps the attribute values in a nested `payload` (`EntityObject`), which no generated
 * form or table could bind to — `BaseEntityFormBuilder` addresses a control by `attrName` on the entity
 * itself, and `BaseEntityListComponent` reads a cell the same way. `DynamicEntityMapper` flattens on the
 * way in and re-nests on the way out, so this shape is what the whole frontend works with and the
 * envelope never leaks past the repository.
 *
 * `version` rides along because the update endpoint requires it for optimistic locking; it is a property
 * of the row, not of the entity's metadata, which is why no attribute descriptor ever names it.
 */
export interface DynamicEntity {
  id?: string;
  version?: number;
  [attrName: string]: unknown;
}

/** The `content` page every list endpoint of the contract answers with. */
export interface EntityDefinitionPage {
  content?: EntityDefinition[];
}

/** Only an `ACTIVE` definition is rendered; a draft is being authored and a deprecated one is on its way out. */
export function isRenderable(definition: EntityDefinition): boolean {
  return definition.status === undefined || definition.status === 'ACTIVE';
}
