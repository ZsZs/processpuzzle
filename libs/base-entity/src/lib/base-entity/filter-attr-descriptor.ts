import { AbstractAttrDescriptor } from './abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from './base-entity-attr.descriptor';
import { FlexboxContainer } from './flexbox.container';

export function filterAttributeDescriptors(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  let attrDescriptors: BaseEntityAttrDescriptor[] = [];
  descriptors.forEach((descriptor) => {
    if (descriptor instanceof BaseEntityAttrDescriptor) attrDescriptors.push(descriptor);
    else if (descriptor instanceof FlexboxContainer) {
      attrDescriptors = attrDescriptors.concat(filterAttributeDescriptors((descriptor as FlexboxContainer).attrDescriptors));
    }
  });
  return attrDescriptors;
}
