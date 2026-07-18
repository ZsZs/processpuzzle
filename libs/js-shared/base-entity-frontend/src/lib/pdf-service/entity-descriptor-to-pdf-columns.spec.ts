import { describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';
import { entityDescriptorToPdfColumns } from './entity-descriptor-to-pdf-columns';

describe('entityDescriptorToPdfColumns', () => {
  it('maps attrName/label to field/header', () => {
    const name = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');

    const [column] = entityDescriptorToPdfColumns([name]);

    expect(column.field).toBe('name');
    expect(column.header).toBe('Name');
  });

  it('excludes attributes with hideInTable === true', () => {
    const visible = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
    const hidden = new BaseEntityAttrDescriptor('secret', FormControlType.TEXT_BOX, 'Secret');
    hidden.hideInTable = true;

    const columns = entityDescriptorToPdfColumns([visible, hidden]);

    expect(columns.map((c) => c.field)).toEqual(['name']);
  });

  it('flattens nested FlexBox descriptors', () => {
    const inner = new BaseEntityAttrDescriptor('city', FormControlType.TEXT_BOX, 'City');
    const flex = new FlexboxDescriptor([inner], FlexDirection.ROW);

    const columns = entityDescriptorToPdfColumns([flex]);

    expect(columns.map((c) => c.field)).toEqual(['city']);
  });

  it('formats CHECKBOX values as ✓ / ✗ and centers them', () => {
    const flag = new BaseEntityAttrDescriptor('active', FormControlType.CHECKBOX, 'Active');

    const [column] = entityDescriptorToPdfColumns([flag]);

    expect(column.align).toBe('center');
    expect(column.formatter?.(true, {})).toBe('✓');
    expect(column.formatter?.(false, {})).toBe('✗');
  });

  it('formats DATE values as locale dates', () => {
    const date = new BaseEntityAttrDescriptor('createdAt', FormControlType.DATE, 'Created');
    const value = new Date('2024-01-18T20:02:27.000Z');

    const [column] = entityDescriptorToPdfColumns([date]);

    expect(column.formatter?.(value, {})).toBe(value.toLocaleDateString());
    expect(column.formatter?.(undefined, {})).toBe('');
  });

  it('joins TAGS arrays', () => {
    const tags = new BaseEntityAttrDescriptor('labels', FormControlType.TAGS, 'Labels');

    const [column] = entityDescriptorToPdfColumns([tags]);

    expect(column.formatter?.(['a', 'b'], {})).toBe('a, b');
  });

  it('renders ARTIFACT name or objectId', () => {
    const artifact = new BaseEntityAttrDescriptor('file', FormControlType.ARTIFACT, 'File');

    const [column] = entityDescriptorToPdfColumns([artifact]);

    expect(column.formatter?.({ name: 'report.pdf' }, {})).toBe('report.pdf');
    expect(column.formatter?.({ objectId: 'obj-1' }, {})).toBe('obj-1');
    expect(column.formatter?.(null, {})).toBe('');
  });

  it('leaves plain text columns without a formatter', () => {
    const text = new BaseEntityAttrDescriptor('description', FormControlType.TEXT_BOX, 'Description');

    const [column] = entityDescriptorToPdfColumns([text]);

    expect(column.formatter).toBeUndefined();
    expect(column.align).toBe('left');
  });
});
