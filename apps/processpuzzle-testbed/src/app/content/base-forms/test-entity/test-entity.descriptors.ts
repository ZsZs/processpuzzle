import { BaseEntityAttrDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { TestEntity, TestEnum } from './test-entity';

const selectables = Object.keys(TestEnum)
  .filter((key: any) => parseInt(key) >= 0)
  .map((key: string) => ({ key: key, value: TestEnum[key as keyof typeof TestEnum] }));
const column_1 = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', true);
column_1.selectables = selectables;
const column_2 = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
column_2.selectables = selectables;
const column_3 = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX, 'Boolean');
column_3.selectables = selectables;
const column_4 = new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX, 'Number', false, { inputType: 'number' });
column_4.selectables = selectables;
const column_5 = new BaseEntityAttrDescriptor('date', FormControlType.DATE, 'Date', false, { inputType: 'date' });
column_5.selectables = selectables;
column_5.label = 'Choose a date';
const column_6 = new BaseEntityAttrDescriptor('enumValue', FormControlType.DROPDOWN, 'Enum');
column_6.selectables = selectables;

export const testEntityDescriptors: BaseEntityAttrDescriptor<TestEntity>[] = [column_1, column_2, column_3, column_4, column_5, column_6];
