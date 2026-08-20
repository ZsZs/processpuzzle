/**
 * The knowledge layer of `base-entity-api.yaml` as the frontend reads it: what an entity *type* is,
 * declared as data rather than as a TypeScript class with a `BaseEntityFacade` beside it.
 *
 * Interfaces rather than classes, and deliberately so: nothing here is constructed, only read. A
 * definition arrives as JSON and is turned into a {@link BaseEntityDescriptor} by
 * `dynamic-entity.descriptor.ts`; the descriptor is the thing the framework already knows how to render,
 * so this model exists only to be mapped away.
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
 * One attribute of a definition — the contract's `BaseEntityAttribute`.
 *
 * `formControlType` is a plain string rather than the frontend's `FormControlType` enum: the contract
 * declares control types the frontend has no component for (`TEXT`, `NUMBER`, `ENUM_SELECT`, …), and
 * narrowing here would push the mapping into a cast at the parse boundary, where there is nothing
 * sensible to do about a value that does not fit.
 */
export interface EntityAttributeDefinition {
  code: string;
  name?: string;
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
}

/** An entity type — the contract's `BaseEntityDefinition`. */
export interface EntityDefinition {
  code: string;
  name: string;
  description?: string;
  status?: EntityDefinitionStatus;
  /** **Codes** of the definitions that may aggregate this one; empty for a stand-alone entity. */
  componentParents?: string[];
  /** True when this definition's payload travels inside its parent's and it has no rows of its own. */
  isEmbedded?: boolean;
  attributes?: EntityAttributeDefinition[];
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
