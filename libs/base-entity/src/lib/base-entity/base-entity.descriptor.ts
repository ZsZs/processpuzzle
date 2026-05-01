import type { AbstractAttrDescriptor } from './abstact-attr.descriptor';
import { filterAttributeDescriptors } from './filter-attr-descriptor';

export interface BaseEntityDescriptor {
  store: any;
  attrDescriptors: AbstractAttrDescriptor[];
  entityName: string;
  entityTitle: string;
}

export function componentIdentification(entityDescriptor: BaseEntityDescriptor): string {
  const attrDescriptor = filterAttributeDescriptors(entityDescriptor.attrDescriptors).find(
    (attrDescriptor) => attrDescriptor.isLinkToDetails === true,
  );

  return attrDescriptor?.attrName ?? '';
}
