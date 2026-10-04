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
    expect(column.formatter?.('true', {})).toBe('✓');
    expect(column.formatter?.(false, {})).toBe('✗');
    expect(column.formatter?.('maybe', {})).toBe('');
  });

  it('formats DATE values in the default style of the given locale', () => {
    const date = new BaseEntityAttrDescriptor('createdAt', FormControlType.DATE, 'Created');
    const value = new Date('2024-01-18T20:02:27.000Z');

    const [column] = entityDescriptorToPdfColumns([date], 'de');

    expect(column.formatter?.(value, {})).toBe(new Intl.DateTimeFormat('de', { dateStyle: 'medium' }).format(value));
    expect(column.formatter?.('2024-03-04', {})).toBe('04.03.2024');
    expect(column.formatter?.(undefined, {})).toBe('');
    expect(column.formatter?.('not-a-date', {})).toBe('not-a-date');
  });

  it("formats DATE values in the attribute's dateFormat", () => {
    const date = new BaseEntityAttrDescriptor('born', FormControlType.DATE, 'Born');
    date.dateFormat = { dateStyle: 'short' };

    const [column] = entityDescriptorToPdfColumns([date], 'hu');

    expect(column.formatter?.('1960-12-09', {})).toBe('1960. 12. 09.');
  });

  it('joins TAGS arrays and coerces non-array values without default object stringification', () => {
    const tags = new BaseEntityAttrDescriptor('labels', FormControlType.TAGS, 'Labels');

    const [column] = entityDescriptorToPdfColumns([tags]);

    expect(column.formatter?.(['a', 'b'], {})).toBe('a, b');
    expect(column.formatter?.(42, {})).toBe('42');
    expect(column.formatter?.(true, {})).toBe('true');
    expect(column.formatter?.(10n, {})).toBe('10');
    expect(column.formatter?.({ a: 1 }, {})).toBe('{"a":1}');
    expect(column.formatter?.(null, {})).toBe('');
    expect(column.formatter?.(Symbol('x'), {})).toBe('');
  });

  it('renders ARTIFACT name or objectId', () => {
    const artifact = new BaseEntityAttrDescriptor('file', FormControlType.ARTIFACT, 'File');

    const [column] = entityDescriptorToPdfColumns([artifact]);

    expect(column.formatter?.({ name: 'report.pdf' }, {})).toBe('report.pdf');
    expect(column.formatter?.({ objectId: 'obj-1' }, {})).toBe('obj-1');
    expect(column.formatter?.(null, {})).toBe('');
    expect(column.formatter?.([{ name: 'a.pdf' }, { objectId: 'obj-2' }, null], {})).toBe('a.pdf, obj-2');
  });

  it('leaves plain text columns without a formatter', () => {
    const text = new BaseEntityAttrDescriptor('description', FormControlType.TEXT_BOX, 'Description');

    const [column] = entityDescriptorToPdfColumns([text]);

    expect(column.formatter).toBeUndefined();
    expect(column.align).toBe('left');
  });
});
