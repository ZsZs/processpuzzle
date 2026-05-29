import { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType } from '@processpuzzle/base-entity';

function createTestEntityComponentAttrDescriptors(): BaseEntityAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  const descAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  const testEntityAttr = new BaseEntityAttrDescriptor('testEntityId', FormControlType.FOREIGN_KEY, 'Test Entity');
  testEntityAttr.disabled = false;
  testEntityAttr.linkedEntityType = 'Test Entity';

  return [nameAttr, descAttr, testEntityAttr];
}

export function createTestEntityComponentDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName: 'Test Entity Component', attrDescriptors: createTestEntityComponentAttrDescriptors() });
}
