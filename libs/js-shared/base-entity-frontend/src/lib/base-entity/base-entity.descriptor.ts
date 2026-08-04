import type { TemplateRef } from '@angular/core';
import { FormControlType, type AbstractAttrDescriptor } from './abstact-attr.descriptor';
import type { BaseEntityAttrDescriptor } from './base-entity-attr.descriptor';
import { filterAttributeDescriptors } from './filter-attr-descriptor';
import { createTestId, toI18nKey } from './base-entity-utility';

export type EntityTitle = string | (() => string);
export type ExtraFormActionsTemplate = () => TemplateRef<unknown> | undefined;

export interface BaseEntityDescriptorOptions {
  store?: unknown;
  attrDescriptors: AbstractAttrDescriptor[];
  entityName: string;
  entityTitle?: EntityTitle;
  extraFormActionsTemplate?: ExtraFormActionsTemplate;
  /**
   * Optional override for the transloco key root of this entity and its attributes. Defaults to the
   * value derived from {@link entityName} (`"Base Entity"` → `base_entity`); set it only when the
   * registered transloco scope differs from that convention.
   */
  i18nScope?: string;
  /**
   * Name of the attribute whose value identifies the selected entity in the
   * {@link BaseEntityStatusbarComponent}. Defaults to the `isLinkToDetails` attribute
   * (see {@link componentIdentification}).
   */
  titleKey?: string;
  isAbstract?: boolean;
  /** Name of the entity this one inherits from. Unrelated to {@link componentParent}. */
  parentEntity?: string;
  route?: string;
  /**
   * Entity name(s) that may aggregate this one as a component. A component *instance* belongs to exactly
   * one parent (1:N containment) — it is created from the parent's form, edited in its own form and
   * deleted outright when removed there. A list is allowed because one component *type* can be hosted by
   * several parent types (an `App Widget` sits under `App Region`, `App Page` and `App Widget`).
   * Leave undefined for a stand-alone entity. Not to be confused with {@link parentEntity}, which is the
   * inheritance supertype.
   */
  componentParent?: string | string[];
  /**
   * True when the component's payload is carried inside its parent's payload — it has no endpoint and no
   * store of its own, and the parent's save persists it. False (the default) when the component is
   * persisted on its own and points back at its parent through a foreign-key attribute. Meaningless
   * without {@link componentParent}, and rejected without it.
   */
  isEmbedded?: boolean;
}

export class BaseEntityDescriptor {
  store: unknown;
  attrDescriptors: AbstractAttrDescriptor[];
  entityName: string;
  entityTitle: EntityTitle;
  extraFormActionsTemplate?: ExtraFormActionsTemplate;
  i18nScope?: string;
  titleKey?: string;
  parentEntity: string | undefined;
  readonly isAbstract: boolean;
  route: string | undefined;
  /** Normalized {@link BaseEntityDescriptorOptions.componentParent}; empty when this is not a component. */
  readonly componentParents: readonly string[];
  readonly isEmbedded: boolean;

  constructor({
    store,
    attrDescriptors,
    entityName,
    entityTitle,
    extraFormActionsTemplate,
    i18nScope,
    titleKey,
    isAbstract,
    parentEntity,
    route,
    componentParent,
    isEmbedded,
  }: BaseEntityDescriptorOptions) {
    this.store = store;
    this.attrDescriptors = attrDescriptors;
    this.entityName = entityName;
    this.entityTitle = entityTitle ?? '';
    this.extraFormActionsTemplate = extraFormActionsTemplate;
    this.i18nScope = i18nScope;
    this.titleKey = titleKey;
    this.isAbstract = isAbstract ?? false;
    this.parentEntity = parentEntity;
    this.route = route;
    this.componentParents = componentParent === undefined ? [] : [componentParent].flat();
    this.isEmbedded = isEmbedded ?? false;
    // Descriptors are built at bootstrap, so an embedded declaration without a parent fails loudly here
    // rather than producing a component that has neither an own endpoint nor a payload to travel in.
    if (this.isEmbedded && this.componentParents.length === 0) {
      throw new Error(`'${entityName}' declares isEmbedded without a componentParent; an embedded component has to name the entity whose payload carries it.`);
    }
    // Stamp the shared transloco key root onto every leaf attribute (recursing through nested flexbox descriptors).
    filterAttributeDescriptors(this.attrDescriptors).forEach((attrDescriptor) => attrDescriptor.setI18nContext(this.scopeRoot()));
  }

  public createTestId(suffix: string): string {
    return createTestId(this.entityName, suffix);
  }

  /** Transloco key root for this entity, derived from {@link entityName} unless {@link i18nScope} overrides it. */
  public scopeRoot(): string {
    return this.i18nScope ?? toI18nKey(this.entityName);
  }

  /**
   * Full transloco key for the entity name: `<scopeRoot>._self`. The reserved `_self` segment keeps the
   * entity name in the same object as its attribute labels without the two colliding.
   */
  public i18nKey(): string {
    return `${this.scopeRoot()}._self`;
  }

  /** True when this entity is a part of another one rather than a stand-alone aggregate root. */
  public isComponent(): boolean {
    return this.componentParents.length > 0;
  }

  public isComponentOf(entityName: string): boolean {
    return this.componentParents.includes(entityName);
  }

  /**
   * Attribute holding this component's reference back to its parent — the `FOREIGN_KEY` attribute whose
   * `linkedEntityType` names one of {@link componentParents}. Returns `undefined` for a stand-alone entity
   * and for an embedded component, neither of which carries such a reference: an embedded component is
   * located by its position in the parent's payload, not by a foreign key.
   */
  public parentReferenceAttrName(): string | undefined {
    if (!this.isComponent() || this.isEmbedded) return undefined;

    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find(
      (attrDescriptor) => attrDescriptor.formControlType === FormControlType.FOREIGN_KEY && attrDescriptor.linkedEntityType !== undefined && this.isComponentOf(attrDescriptor.linkedEntityType),
    );

    return attrDescriptor?.attrName;
  }

  componentIdentification(): string {
    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find((attrDescriptor) => attrDescriptor.isLinkToDetails === true);

    return attrDescriptor?.attrName ?? '';
  }

  /** The attributes that carry embedded children — one route branch, and one row list, per entry. */
  public embeddedAttrDescriptors(): BaseEntityAttrDescriptor[] {
    return filterAttributeDescriptors(this.attrDescriptors).filter((attrDescriptor) => attrDescriptor.formControlType === FormControlType.EMBEDDED_COMPONENTS);
  }

  /**
   * The attribute carrying `entityName`'s rows. An embedded child is addressed in the URL by its entity
   * name, so two attributes offering the same child type would make that segment ambiguous — hence the
   * error rather than a silent first-match.
   */
  public embeddedAttrFor(entityName: string): BaseEntityAttrDescriptor | undefined {
    const candidates = this.embeddedAttrDescriptors().filter((attrDescriptor) => attrDescriptor.linkedEntityType === entityName);
    if (candidates.length > 1) {
      const attrNames = candidates.map((attrDescriptor) => `'${attrDescriptor.attrName}'`).join(', ');
      throw new Error(`'${this.entityName}' declares ${attrNames} as embedded '${entityName}' components; an embedded child type may be carried by only one attribute, because the route segment names the entity.`);
    }

    return candidates[0];
  }

  /** Name of the attribute whose value labels the selected entity in the status bar. */
  public titleAttrName(): string {
    return this.titleKey ?? this.componentIdentification();
  }

  public overwriteLinkedEntityAttr(attrName: string, linkedEntityName: string): void {
    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find((attrDescriptor) => attrDescriptor.attrName === attrName);

    if (attrDescriptor) {
      attrDescriptor.linkedEntityType = linkedEntityName;
    }
  }
}
