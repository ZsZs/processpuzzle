// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType } from '@processpuzzle/base-entity';

function createRelatedEntityAttrDescriptors(): BaseEntityAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');

  return [nameAttr, descriptionAttr];
}

export function createRelatedEntityDescriptor(): BaseEntityDescriptor {
  // A stand-alone aggregate root: no `componentParent`, so nothing owns it and nothing deletes it on its
  // behalf. `Test Entity` merely points at it through a RELATED_ENTITIES attribute.
  return new BaseEntityDescriptor({
    entityName: 'Related Entity',
    attrDescriptors: createRelatedEntityAttrDescriptors(),
  });
}
