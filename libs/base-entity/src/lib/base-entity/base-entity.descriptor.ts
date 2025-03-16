import { AbstractAttrDescriptor } from './abstact-attr.descriptor';

export interface BaseEntityDescriptor {
  store: any;
  attrDescriptors: AbstractAttrDescriptor[];
  entityName: string;
  entityTitle: string;
}
