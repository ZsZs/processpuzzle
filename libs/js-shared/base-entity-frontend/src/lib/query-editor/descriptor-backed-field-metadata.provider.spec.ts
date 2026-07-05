import { describe, expect, it } from 'vitest';
import { DescriptorBackedFieldMetadataProvider } from './descriptor-backed-field-metadata.provider';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';
import { RSQL_OPERATORS_BY_TYPE } from './rsql-field-metadata.model';

describe('DescriptorBackedFieldMetadataProvider', () => {
  it('maps each FormControlType to the expected RSQL field type', () => {
    const descriptors = [
      new BaseEntityAttrDescriptor('flag', FormControlType.CHECKBOX),
      new BaseEntityAttrDescriptor('when', FormControlType.DATE),
      new BaseEntityAttrDescriptor('kind', FormControlType.DROPDOWN, undefined, [{ key: 'A', value: 1 }]),
      new BaseEntityAttrDescriptor('choice', FormControlType.RADIO, undefined, [{ key: 'X', value: 'x' }]),
      new BaseEntityAttrDescriptor('parent', FormControlType.FOREIGN_KEY),
      new BaseEntityAttrDescriptor('tenant', FormControlType.LOOKUP),
      new BaseEntityAttrDescriptor('tags', FormControlType.TAGS),
      new BaseEntityAttrDescriptor('notes', FormControlType.TEXTAREA),
      new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX),
    ];

    const provider = new DescriptorBackedFieldMetadataProvider(descriptors);
    const byName = Object.fromEntries(provider.getFields().map((f) => [f.name, f]));

    expect(byName['flag'].type).toBe('boolean');
    expect(byName['when'].type).toBe('date');
    expect(byName['kind'].type).toBe('enum');
    expect(byName['choice'].type).toBe('enum');
    expect(byName['parent'].type).toBe('string');
    expect(byName['tenant'].type).toBe('string');
    expect(byName['tags'].type).toBe('string');
    expect(byName['notes'].type).toBe('string');
    expect(byName['title'].type).toBe('string');
  });

  it('skips descriptors whose control types cannot be filtered on', () => {
    const descriptors = [
      new BaseEntityAttrDescriptor('artifact', FormControlType.ARTIFACT),
      new BaseEntityAttrDescriptor('components', FormControlType.COMPONENTS),
      new BaseEntityAttrDescriptor('label', FormControlType.LABEL),
      new BaseEntityAttrDescriptor('title', FormControlType.TITLE),
      new BaseEntityAttrDescriptor('kept', FormControlType.TEXT_BOX),
    ];

    const provider = new DescriptorBackedFieldMetadataProvider(descriptors);

    expect(provider.getFields().map((f) => f.name)).toEqual(['kept']);
  });

  it('unwraps flexbox children through filterAttributeDescriptors', () => {
    const inner = new BaseEntityAttrDescriptor('nested', FormControlType.TEXT_BOX);
    const flex = new FlexboxDescriptor([inner], FlexDirection.ROW);
    const outer = new BaseEntityAttrDescriptor('outer', FormControlType.TEXT_BOX);

    const provider = new DescriptorBackedFieldMetadataProvider([outer, flex]);

    expect(provider.getFields().map((f) => f.name).sort()).toEqual(['nested', 'outer']);
  });

  it('overrides text-box type with options.inputType when it maps to a known RSQL type', () => {
    const numeric = new BaseEntityAttrDescriptor('count', FormControlType.TEXT_BOX, undefined, undefined, undefined, { inputType: 'number' });
    const dateInput = new BaseEntityAttrDescriptor('startDate', FormControlType.TEXT_BOX, undefined, undefined, undefined, { inputType: 'date' });
    const datetime = new BaseEntityAttrDescriptor('startAt', FormControlType.TEXT_BOX, undefined, undefined, undefined, { inputType: 'datetime' });
    const datetimeLocal = new BaseEntityAttrDescriptor('endAt', FormControlType.TEXT_BOX, undefined, undefined, undefined, { inputType: 'datetime-local' });
    const unknown = new BaseEntityAttrDescriptor('other', FormControlType.TEXT_BOX, undefined, undefined, undefined, { inputType: 'color' });

    const provider = new DescriptorBackedFieldMetadataProvider([numeric, dateInput, datetime, datetimeLocal, unknown]);
    const byName = Object.fromEntries(provider.getFields().map((f) => [f.name, f]));

    expect(byName['count'].type).toBe('number');
    expect(byName['startDate'].type).toBe('date');
    expect(byName['startAt'].type).toBe('datetime');
    expect(byName['endAt'].type).toBe('datetime');
    expect(byName['other'].type).toBe('string');
  });

  it('populates enumValues for enum fields and leaves them undefined otherwise', () => {
    const dropdown = new BaseEntityAttrDescriptor('color', FormControlType.DROPDOWN, undefined, [
      { key: 'red', value: 1 },
      { key: 'blue', value: 2 },
    ]);
    const textbox = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX);
    const noSelectables = new BaseEntityAttrDescriptor('mode', FormControlType.DROPDOWN);

    const provider = new DescriptorBackedFieldMetadataProvider([dropdown, textbox, noSelectables]);
    const fields = provider.getFields();
    const color = fields.find((f) => f.name === 'color')!;
    const name = fields.find((f) => f.name === 'name')!;
    const mode = fields.find((f) => f.name === 'mode')!;

    expect(color.enumValues).toEqual(['red', 'blue']);
    expect(name.enumValues).toBeUndefined();
    expect(mode.enumValues).toEqual([]);
  });

  it('exposes the label from the descriptor and the type-appropriate operator set', () => {
    const descriptor = new BaseEntityAttrDescriptor('priority', FormControlType.TEXT_BOX, 'Priority', undefined, undefined, { inputType: 'number' });

    const provider = new DescriptorBackedFieldMetadataProvider([descriptor]);
    const [field] = provider.getFields();

    expect(field.label).toBe('Priority');
    expect(field.operators).toEqual(RSQL_OPERATORS_BY_TYPE['number']);
  });

  it('getField returns the descriptor by attrName and undefined for unknown names', () => {
    const descriptor = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX);

    const provider = new DescriptorBackedFieldMetadataProvider([descriptor]);

    expect(provider.getField('title')?.name).toBe('title');
    expect(provider.getField('missing')).toBeUndefined();
  });
});
