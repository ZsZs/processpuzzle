import { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType } from '@processpuzzle/base-entity';

function createTestEntityComponentAttrDescriptors(): BaseEntityAttrDescriptor[] {
  const column_1 = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  const column_2 = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  const column_3 = new BaseEntityAttrDescriptor('testEntityId', FormControlType.FOREIGN_KEY, 'Test Entity');
  column_3.disabled = true;
  //  column_3.linkedEntityType = testEntityDescriptor;

  return [column_1, column_2, column_3];
}

export function createTestEntityComponentDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: 'Test Entity Component',
    attrDescriptors: createTestEntityComponentAttrDescriptors(),
  });
}
