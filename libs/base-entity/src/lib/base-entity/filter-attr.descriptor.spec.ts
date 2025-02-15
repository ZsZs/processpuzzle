import { BaseEntityAttrDescriptor } from './base-entity-attr.descriptor';
import { FlexboxContainer, FlexDirection } from './flexbox.container';
import { filterAttributeDescriptors } from './filter-attr-descriptor';

describe('filterAttributeDescriptors()', () => {
  const row1 = new FlexboxContainer([new BaseEntityAttrDescriptor('attr1'), new BaseEntityAttrDescriptor('attr2')], FlexDirection.ROW);
  const row2 = new FlexboxContainer([new BaseEntityAttrDescriptor('attr3'), new BaseEntityAttrDescriptor('attr4')], FlexDirection.ROW);
  const row3 = new FlexboxContainer([new BaseEntityAttrDescriptor('attr5'), new BaseEntityAttrDescriptor('attr6')], FlexDirection.ROW);
  const column1 = new FlexboxContainer([row1, row2], FlexDirection.COLUMN);
  const column2 = new FlexboxContainer([row3], FlexDirection.COLUMN);

  it('should filter out ControlLayoutDescriptors', () => {
    expect(filterAttributeDescriptors([row1, row2]).length).toBe(4);
    expect(filterAttributeDescriptors([column1, column2]).length).toBe(6);
  });

  it('return attributes in order', () => {
    expect(filterAttributeDescriptors([column1, column2]).map((attr) => attr.attrName)).toEqual(['attr1', 'attr2', 'attr3', 'attr4', 'attr5', 'attr6']);
  });
});
