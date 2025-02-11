import { BaseEntityAttrDescriptor, FormControlType } from '@processpuzzle/base-entity';

const column_1 = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
const column_2 = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
const column_3 = new BaseEntityAttrDescriptor('value', FormControlType.TEXT_BOX, 'Value');

export const trunkDataDescriptors: BaseEntityAttrDescriptor[] = [column_1, column_2, column_3];
