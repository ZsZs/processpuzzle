import { BaseEntityAttrDescriptor } from './base-entity-attr.descriptor';

export interface BaseEntityDescriptor {
  store: any;
  attrDescriptors: BaseEntityAttrDescriptor<any>[];
  entityName: string;
  entityTitle: string;
}
