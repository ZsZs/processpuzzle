import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { TestEnum } from './test-entity';
import { createTestEntityComponentDescriptor } from '../test-entity-component/test-entity-component.descriptors';
import { createTrunkDataDescriptor } from '../trunk-data/trunk-data.descriptors';

const selectables = Object.keys(TestEnum)
  .filter((key: any) => parseInt(key) >= 0)
  .map((key: string) => ({ key: key, value: TestEnum[key as keyof typeof TestEnum] }));

function createTestEntityAttrDescriptors(): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  const booleanAttr = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX, 'Boolean');
  const numberAttr = new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX, 'Number', undefined, false, { inputType: 'number' });
  const dateAttr = new BaseEntityAttrDescriptor('date', FormControlType.DATE, 'Date', undefined, false, { inputType: 'date' });
  const lookupAttr = new BaseEntityAttrDescriptor('lookup', FormControlType.LOOKUP, 'Lookup', undefined, false);
  const enumAttr = new BaseEntityAttrDescriptor('enumValue', FormControlType.DROPDOWN, 'Enum', selectables);
  const artifactAttr = new BaseEntityAttrDescriptor('artifact', FormControlType.ARTIFACT, 'Artifact');
  const tagsAttr = new BaseEntityAttrDescriptor('tags', FormControlType.TAGS, 'Tags');
  const componentsAttr = new BaseEntityAttrDescriptor('components', FormControlType.COMPONENTS, 'Components');
  lookupAttr.linkedEntityType = createTrunkDataDescriptor();
  componentsAttr.linkedEntityType = createTestEntityComponentDescriptor();

  const column_1 = new FlexboxDescriptor([nameAttr, descriptionAttr, booleanAttr, artifactAttr], FlexDirection.COLUMN);
  const column_2 = new FlexboxDescriptor([numberAttr, dateAttr, lookupAttr, enumAttr, tagsAttr, componentsAttr], FlexDirection.COLUMN);
  const flexBoxContainer = new FlexboxDescriptor([column_1, column_2], FlexDirection.CONTAINER);
  flexBoxContainer.style = { 'column-gap': '20px' };
  return [flexBoxContainer];
}

let cachedDescriptor: BaseEntityDescriptor | undefined;

export function createTestEntityDescriptor(): BaseEntityDescriptor {
  if (cachedDescriptor) return cachedDescriptor;
  cachedDescriptor = new BaseEntityDescriptor({ entityName: 'Test Entity', attrDescriptors: [] });
  cachedDescriptor.attrDescriptors = createTestEntityAttrDescriptors();
  return cachedDescriptor;
}
