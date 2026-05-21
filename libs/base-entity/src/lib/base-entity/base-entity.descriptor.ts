import type { AbstractAttrDescriptor } from './abstact-attr.descriptor';
import { filterAttributeDescriptors } from './filter-attr-descriptor';
import { createTestId } from './base-entity-utility';

export interface BaseEntityDescriptorOptions {
  store?: any;
  attrDescriptors: AbstractAttrDescriptor[];
  entityName: string;
  entityTitle?: string;
}

export class BaseEntityDescriptor {
  store: any;
  attrDescriptors: AbstractAttrDescriptor[];
  entityName: string;
  entityTitle: string;
  parentEntity: string | undefined;
  readonly isAbstract: boolean;

  constructor({ store, attrDescriptors, entityName, entityTitle }: BaseEntityDescriptorOptions, isAbstract?: boolean) {
    this.store = store;
    this.attrDescriptors = attrDescriptors;
    this.entityName = entityName;
    this.entityTitle = entityTitle ?? '';
    this.isAbstract = isAbstract ?? false;
  }

  public createTestId(suffix: string): string {
    return createTestId(this.entityName, suffix);
  }

  componentIdentification(): string {
    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find((attrDescriptor) => attrDescriptor.isLinkToDetails === true);

    return attrDescriptor?.attrName ?? '';
  }

  public overwriteLinkedEntityAttr(attrName: string, linkedEntityDescr: BaseEntityDescriptor): void {
    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find((attrDescriptor) => attrDescriptor.attrName === attrName);

    if (attrDescriptor) {
      attrDescriptor.linkedEntityType = linkedEntityDescr;
    }
  }
}
