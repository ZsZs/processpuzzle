import { BaseEntityAttrDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { TestEntity } from '../test-entity/test-entity';

const column_1 = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', true);
const column_2 = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
const column_3 = new BaseEntityAttrDescriptor('testEntityId', FormControlType.FOREIGN_KEY, 'Test Entity');
column_3.disabled = true;
column_3.setLinkedEntityType(TestEntity);

export const testEntityComponentDescriptors: BaseEntityAttrDescriptor<TestEntity>[] = [column_1, column_2, column_3];
