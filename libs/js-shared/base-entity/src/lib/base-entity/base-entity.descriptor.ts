import type { AbstractAttrDescriptor } from './abstact-attr.descriptor';
import { filterAttributeDescriptors } from './filter-attr-descriptor';
import { createTestId } from './base-entity-utility';

export type EntityTitle = string | (() => string);

export interface BaseEntityDescriptorOptions {
  store?: unknown;
  attrDescriptors: AbstractAttrDescriptor[];
  entityName: string;
  entityTitle?: EntityTitle;
  isAbstract?: boolean;
  parentEntity?: string;
}

export class BaseEntityDescriptor {
  store: unknown;
  attrDescriptors: AbstractAttrDescriptor[];
  entityName: string;
  entityTitle: EntityTitle;
  parentEntity: string | undefined;
  readonly isAbstract: boolean;

  constructor({ store, attrDescriptors, entityName, entityTitle, isAbstract, parentEntity }: BaseEntityDescriptorOptions) {
    this.store = store;
    this.attrDescriptors = attrDescriptors;
    this.entityName = entityName;
    this.entityTitle = entityTitle ?? '';
    this.isAbstract = isAbstract ?? false;
    this.parentEntity = parentEntity;
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
