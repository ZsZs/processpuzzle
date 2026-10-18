import { describe, expect, it } from 'vitest';
import { newTable, readTables, removeTable, writeTable, yamlErrorsOf } from './calculation-source';
import { ORDER_TOTAL_SOURCE } from './test-calculation-definition';

describe('calculation-source', () => {
  it('reads the tables in source order', () => {
    const [table] = readTables(ORDER_TOTAL_SOURCE);

    expect(table).toEqual({
      name: 'volume-discount',
      hitPolicy: 'FIRST',
      inputs: ['subtotal', 'customerTier'],
      output: 'discountRate',
      rules: [
        { when: ['>= 10000', '"GOLD"'], then: '0.1' },
        { when: ['-', '-'], then: '0' },
      ],
    });
  });

  it('reads nothing from a source without tables or that does not parse', () => {
    expect(readTables('calculation: x\n')).toEqual([]);
    expect(readTables('calculation: [')).toEqual([]);
    expect(yamlErrorsOf('calculation: [')).not.toEqual([]);
    expect(yamlErrorsOf(ORDER_TOTAL_SOURCE)).toEqual([]);
  });

  it('defaults what a hand-written table leaves out', () => {
    const [table] = readTables('tables:\n  t:\n    hitPolicy: sideways\n    rules: [ { when: [~] } ]\n');

    expect(table).toEqual({ name: 't', hitPolicy: 'FIRST', inputs: [], output: '', rules: [{ when: ['-'], then: '' }] });
  });

  it('rewrites one table and keeps the comments of the rest', () => {
    const [table] = readTables(ORDER_TOTAL_SOURCE);
    const written = writeTable(ORDER_TOTAL_SOURCE, { ...table, hitPolicy: 'UNIQUE', rules: [...table.rules, { when: ['>= 1000', '-'], then: 'rate * 2' }] });

    expect(written).toContain('# The order total');
    expect(written).toContain('# Discount tiers');
    expect(written).toContain('hitPolicy: UNIQUE');
    expect(written).toContain('inputs: [ subtotal, customerTier ]');
    expect(readTables(written)[0].rules[2]).toEqual({ when: ['>= 1000', '-'], then: 'rate * 2' });
    expect(readTables(written)[0].rules[0].then).toBe('0.1');
  });

  it('renames a table in place and adds a new one at the end', () => {
    const [table] = readTables(ORDER_TOTAL_SOURCE);
    const renamed = writeTable(ORDER_TOTAL_SOURCE, { ...table, name: 'discounts' }, 'volume-discount');
    const added = writeTable(renamed, newTable('extra'));

    expect(readTables(added).map((each) => each.name)).toEqual(['discounts', 'extra']);
    expect(readTables(removeTable(added, 'discounts')).map((each) => each.name)).toEqual(['extra']);
  });

  it('creates the tables section when there is none', () => {
    expect(readTables(writeTable('calculation: x\n', newTable('t')))).toEqual([newTable('t')]);
  });

  it('refuses to rewrite a source that does not parse', () => {
    expect(() => writeTable('calculation: [', newTable('t'))).toThrow();
    expect(() => removeTable('calculation: [', 't')).toThrow();
  });
});
