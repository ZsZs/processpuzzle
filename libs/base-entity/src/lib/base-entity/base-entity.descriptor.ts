import type { AbstractAttrDescriptor } from './abstact-attr.descriptor';
import { filterAttributeDescriptors } from './filter-attr-descriptor';

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

  constructor({ store, attrDescriptors, entityName, entityTitle }: BaseEntityDescriptorOptions) {
    this.store = store;
    this.attrDescriptors = attrDescriptors;
    this.entityName = entityName;
    this.entityTitle = entityTitle ?? '';
  }

  componentIdentification(): string {
    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find(
      (attrDescriptor) => attrDescriptor.isLinkToDetails === true,
    );

    return attrDescriptor?.attrName ?? '';
  }

  public overwriteLinkedEntityAttr(attrName: string, linkedEntityDescr: BaseEntityDescriptor): void {
    const attrDescriptor = filterAttributeDescriptors(this.attrDescriptors).find(
      (attrDescriptor) => attrDescriptor.attrName === attrName,
    );

    if (attrDescriptor) {
      attrDescriptor.linkedEntityType = linkedEntityDescr;
    }
  }
}
