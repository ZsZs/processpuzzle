import { AbstractAttrDescriptor } from './abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from './base-entity-attr.descriptor';
import { FlexboxDescriptor } from './flexboxDescriptor';

export function filterAttributeDescriptors(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  let attrDescriptors: BaseEntityAttrDescriptor[] = [];
  descriptors.forEach((descriptor) => {
    if (descriptor instanceof BaseEntityAttrDescriptor) attrDescriptors.push(descriptor);
    else if (descriptor instanceof FlexboxDescriptor) {
      attrDescriptors = attrDescriptors.concat(filterAttributeDescriptors((descriptor as FlexboxDescriptor).attrDescriptors));
    }
  });
  return attrDescriptors;
}
