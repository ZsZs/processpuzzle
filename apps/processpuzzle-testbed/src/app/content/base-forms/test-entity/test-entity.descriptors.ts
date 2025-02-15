import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxContainer, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { TestEnum } from './test-entity';

const selectables = Object.keys(TestEnum)
  .filter((key: any) => parseInt(key) >= 0)
  .map((key: string) => ({ key: key, value: TestEnum[key as keyof typeof TestEnum] }));

const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
const booleanAttr = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX, 'Boolean');
const numberAttr = new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX, 'Number', undefined, false, { inputType: 'number' });
const dateAttr = new BaseEntityAttrDescriptor('date', FormControlType.DATE, 'Date', undefined, false, { inputType: 'date' });
const enumAttr = new BaseEntityAttrDescriptor('enumValue', FormControlType.DROPDOWN, 'Enum', selectables);

const column_1 = new FlexboxContainer([nameAttr, descriptionAttr, booleanAttr], FlexDirection.COLUMN);
const column_2 = new FlexboxContainer([numberAttr, dateAttr, enumAttr], FlexDirection.COLUMN);
const flexBoxContainer = new FlexboxContainer([column_1, column_2], FlexDirection.CONTAINER);
flexBoxContainer.style = { 'column-gap': '20px' };
export const testEntityDescriptors: AbstractAttrDescriptor[] = [flexBoxContainer];
