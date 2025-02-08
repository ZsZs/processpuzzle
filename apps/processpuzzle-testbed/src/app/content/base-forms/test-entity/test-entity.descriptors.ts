import { BaseEntityAttrDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { TestEntity, TestEnum } from './test-entity';

const selectables = Object.keys(TestEnum)
  .filter((key: any) => parseInt(key) >= 0)
  .map((key: string) => ({ key: key, value: TestEnum[key as keyof typeof TestEnum] }));

const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
const booleanAttr = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX, 'Boolean');
const numberAttr = new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX, 'Number', undefined, false, { inputType: 'number' });
const dateAttr = new BaseEntityAttrDescriptor('date', FormControlType.DATE, 'Date', undefined, false, { inputType: 'date' });
const enumAttr = new BaseEntityAttrDescriptor('enumValue', FormControlType.DROPDOWN, 'Enum', selectables);

export const testEntityDescriptors: BaseEntityAttrDescriptor<TestEntity>[] = [nameAttr, descriptionAttr, booleanAttr, numberAttr, dateAttr, enumAttr];
