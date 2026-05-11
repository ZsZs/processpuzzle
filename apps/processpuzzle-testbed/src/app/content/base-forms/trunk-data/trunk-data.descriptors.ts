import { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType } from '@processpuzzle/base-entity';

const column_1 = new BaseEntityAttrDescriptor('key', FormControlType.TEXT_BOX, 'Key', undefined, false);
column_1.required = true;
const column_2 = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
column_2.required = true;
const column_3 = new BaseEntityAttrDescriptor('value', FormControlType.TEXT_BOX, 'Value');

const trunkDataDescriptors: BaseEntityAttrDescriptor[] = [column_1, column_2, column_3];

export function createTrunkDataDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: 'Trunk Data',
    attrDescriptors: trunkDataDescriptors,
  });
}
