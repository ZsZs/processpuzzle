import { BaseEntityAttrDescriptor } from './base-entity-attr.descriptor';
import { FlexboxDescriptor, FlexDirection } from './flexboxDescriptor';
import { filterAttributeDescriptors } from './filter-attr-descriptor';
import { FormControlType } from './abstact-attr.descriptor';

describe('filterAttributeDescriptors()', () => {
  const row1 = new FlexboxDescriptor([new BaseEntityAttrDescriptor('attr1', FormControlType.TEXT_BOX), new BaseEntityAttrDescriptor('attr2', FormControlType.TEXT_BOX)], FlexDirection.ROW);
  const row2 = new FlexboxDescriptor([new BaseEntityAttrDescriptor('attr3', FormControlType.TEXT_BOX), new BaseEntityAttrDescriptor('attr4', FormControlType.TEXT_BOX)], FlexDirection.ROW);
  const row3 = new FlexboxDescriptor([new BaseEntityAttrDescriptor('attr5', FormControlType.TEXT_BOX), new BaseEntityAttrDescriptor('attr6', FormControlType.TEXT_BOX)], FlexDirection.ROW);
  const column1 = new FlexboxDescriptor([row1, row2], FlexDirection.COLUMN);
  const column2 = new FlexboxDescriptor([row3], FlexDirection.COLUMN);

  it('should filter out ControlLayoutDescriptors', () => {
    expect(filterAttributeDescriptors([row1, row2]).length).toBe(4);
    expect(filterAttributeDescriptors([column1, column2]).length).toBe(6);
  });

  it('return attributes in order', () => {
    expect(filterAttributeDescriptors([column1, column2]).map((attr) => attr.attrName)).toEqual(['attr1', 'attr2', 'attr3', 'attr4', 'attr5', 'attr6']);
  });
});
