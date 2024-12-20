import { BaseEntityAttrDescriptor } from './base-entity-attr.descriptor';

export interface BaseEntityDescriptor {
  store: any;
  columnDescriptors: BaseEntityAttrDescriptor<any>[];
  entityName: string;
  entityTitle: string;
}
