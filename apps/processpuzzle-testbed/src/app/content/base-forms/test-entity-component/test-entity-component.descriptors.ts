import { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createTestEntityDescriptor } from '../test-entity/test-entity.descriptors';

function createTestEntityComponentAttrDescriptors(): BaseEntityAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  const descAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  const testEntityAttr = new BaseEntityAttrDescriptor('testEntityId', FormControlType.FOREIGN_KEY, 'Test Entity');
  testEntityAttr.disabled = false;
  testEntityAttr.linkedEntityType = createTestEntityDescriptor();

  return [nameAttr, descAttr, testEntityAttr];
}

let cachedDescriptor: BaseEntityDescriptor | undefined;

export function createTestEntityComponentDescriptor(): BaseEntityDescriptor {
  if (cachedDescriptor) return cachedDescriptor;
  cachedDescriptor = new BaseEntityDescriptor({ entityName: 'Test Entity Component', attrDescriptors: [] });
  cachedDescriptor.attrDescriptors = createTestEntityComponentAttrDescriptors();
  return cachedDescriptor;
}
