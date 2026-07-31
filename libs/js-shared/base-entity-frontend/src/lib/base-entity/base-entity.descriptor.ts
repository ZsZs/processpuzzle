import type { TemplateRef } from '@angular/core';
import type { AbstractAttrDescriptor } from './abstact-attr.descriptor';
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
  parentEntity?: string;
  route?: string;
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

  constructor({ store, attrDescriptors, entityName, entityTitle, extraFormActionsTemplate, i18nScope, titleKey, isAbstract, parentEntity, route }: BaseEntityDescriptorOptions) {
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

  componentIdentification(): string {
    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find((attrDescriptor) => attrDescriptor.isLinkToDetails === true);

    return attrDescriptor?.attrName ?? '';
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
