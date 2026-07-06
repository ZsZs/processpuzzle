import { describe, expect, it } from 'vitest';
import { FilterCondition, FilterGroup } from './base-entity-load-response';
import { toRsql } from './rsql';

describe('toRsql', () => {
  it('joins flat FilterCondition[] with AND (;)', () => {
    const filters: FilterCondition[] = [
      { property: 'name', operator: '==', value: 'foo' },
      { property: 'age', operator: '>', value: '18' },
    ];
    expect(toRsql(filters)).toBe('name==foo;age=gt=18');
  });

  it('returns empty string for empty condition list', () => {
    expect(toRsql([])).toBe('');
  });

  it('maps all comparison operators', () => {
    expect(toRsql([{ property: 'a', operator: '==', value: '1' }])).toBe('a==1');
    expect(toRsql([{ property: 'a', operator: '!=', value: '1' }])).toBe('a!=1');
    expect(toRsql([{ property: 'a', operator: '<', value: '1' }])).toBe('a=lt=1');
    expect(toRsql([{ property: 'a', operator: '<=', value: '1' }])).toBe('a=le=1');
    expect(toRsql([{ property: 'a', operator: '>', value: '1' }])).toBe('a=gt=1');
    expect(toRsql([{ property: 'a', operator: '>=', value: '1' }])).toBe('a=ge=1');
  });

  it('emits =in= with parenthesised list for in / not-in', () => {
    expect(toRsql([{ property: 'status', operator: 'in', value: ['DRAFT', 'SHIPPED'] as unknown as string }])).toBe('status=in=(DRAFT,SHIPPED)');
    expect(toRsql([{ property: 'status', operator: 'not-in', value: ['CANCELLED'] as unknown as string }])).toBe('status=out=(CANCELLED)');
  });

  it('emits =in= for array-contains against the single value', () => {
    expect(toRsql([{ property: 'tags', operator: 'array-contains', value: 'urgent' }])).toBe('tags=in=(urgent)');
  });

  it('quotes values that contain RSQL-reserved characters', () => {
    expect(toRsql([{ property: 'name', operator: '==', value: 'John Doe' }])).toBe('name=="John Doe"');
    expect(toRsql([{ property: 'name', operator: '==', value: 'a;b' }])).toBe('name=="a;b"');
    expect(toRsql([{ property: 'name', operator: '==', value: 'a"b' }])).toBe('name=="a\\"b"');
  });

  it('renders AND group with ; and OR group with ,', () => {
    const and: FilterGroup = {
      operator: 'AND',
      conditions: [
        { property: 'a', operator: '==', value: '1' },
        { property: 'b', operator: '==', value: '2' },
      ],
    };
    expect(toRsql(and)).toBe('a==1;b==2');

    const or: FilterGroup = { ...and, operator: 'OR' };
    expect(toRsql(or)).toBe('a==1,b==2');
  });

  it('parenthesises nested groups', () => {
    const group: FilterGroup = {
      operator: 'AND',
      conditions: [
        { property: 'status', operator: '==', value: 'SHIPPED' },
        {
          operator: 'OR',
          conditions: [
            { property: 'total', operator: '>', value: '100' },
            { property: 'priority', operator: '==', value: 'HIGH' },
          ],
        },
      ],
    };
    expect(toRsql(group)).toBe('status==SHIPPED;(total=gt=100,priority==HIGH)');
  });

  it('renders null values as the literal null', () => {
    expect(toRsql([{ property: 'shippingAddress', operator: '==', value: null as unknown as string }])).toBe('shippingAddress==null');
  });
});
