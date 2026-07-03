import type { TemplateRef } from '@angular/core';
import type { AbstractAttrDescriptor } from './abstact-attr.descriptor';
import { filterAttributeDescriptors } from './filter-attr-descriptor';
import { createTestId } from './base-entity-utility';

export type EntityTitle = string | (() => string);
export type ExtraFormActionsTemplate = () => TemplateRef<unknown> | undefined;

export interface BaseEntityDescriptorOptions {
  store?: unknown;
  attrDescriptors: AbstractAttrDescriptor[];
  entityName: string;
  entityTitle?: EntityTitle;
  extraFormActionsTemplate?: ExtraFormActionsTemplate;
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
  parentEntity: string | undefined;
  readonly isAbstract: boolean;
  route: string | undefined;

  constructor({ store, attrDescriptors, entityName, entityTitle, extraFormActionsTemplate, isAbstract, parentEntity, route }: BaseEntityDescriptorOptions) {
    this.store = store;
    this.attrDescriptors = attrDescriptors;
    this.entityName = entityName;
    this.entityTitle = entityTitle ?? '';
    this.extraFormActionsTemplate = extraFormActionsTemplate;
    this.isAbstract = isAbstract ?? false;
    this.parentEntity = parentEntity;
    this.route = route;
  }

  public createTestId(suffix: string): string {
    return createTestId(this.entityName, suffix);
  }

  componentIdentification(): string {
    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find((attrDescriptor) => attrDescriptor.isLinkToDetails === true);

    return attrDescriptor?.attrName ?? '';
  }

  public overwriteLinkedEntityAttr(attrName: string, linkedEntityName: string): void {
    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find((attrDescriptor) => attrDescriptor.attrName === attrName);

    if (attrDescriptor) {
      attrDescriptor.linkedEntityType = linkedEntityName;
    }
  }
}
