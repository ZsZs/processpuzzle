import { describe, expect, it } from 'vitest';
import { appendRow, findRow, indexOfRow, readOwner, readRows, removeRow, replaceRow, rowId, writeRows } from './embedded-aggregate';

/** Mirrors the testbed's shape: a root with embedded rows that themselves carry embedded rows. */
function anAggregate() {
  return {
    id: '1',
    name: 'entity_1',
    embeddedComponents: [
      { id: 'embedded_1_1', name: 'embedded_one', embeddedDetails: [{ id: 'detail_1', name: 'detail one' }] },
      { id: 'embedded_1_2', name: 'embedded_two' },
    ],
  };
}

describe('embedded-aggregate', () => {
  describe('readOwner', () => {
    it('returns the root itself for the empty path', () => {
      const root = anAggregate();

      expect(readOwner(root, [])).toBe(root);
    });

    it('follows a nested path', () => {
      const root = anAggregate();

      expect(readOwner(root, [{ attrName: 'embeddedComponents', index: 0 }])).toEqual(root.embeddedComponents[0]);
    });

    it('returns undefined when a hop is missing', () => {
      expect(readOwner(anAggregate(), [{ attrName: 'embeddedComponents', index: 7 }])).toBeUndefined();
      expect(readOwner(anAggregate(), [{ attrName: 'nonexistent', index: 0 }])).toBeUndefined();
      expect(readOwner(undefined, [])).toBeUndefined();
    });
  });

  describe('readRows', () => {
    it('reads the rows of an attribute at the root', () => {
      expect(readRows(anAggregate(), [], 'embeddedComponents')).toHaveLength(2);
    });

    it('reads the rows of an attribute one level down', () => {
      const rows = readRows(anAggregate(), [{ attrName: 'embeddedComponents', index: 0 }], 'embeddedDetails');

      expect(rows).toEqual([{ id: 'detail_1', name: 'detail one' }]);
    });

    it('returns an empty array for an unset attribute rather than throwing', () => {
      expect(readRows(anAggregate(), [{ attrName: 'embeddedComponents', index: 1 }], 'embeddedDetails')).toEqual([]);
      expect(readRows(anAggregate(), [{ attrName: 'embeddedComponents', index: 7 }], 'embeddedDetails')).toEqual([]);
    });
  });

  describe('writeRows', () => {
    it('replaces the rows at the root without mutating the source', () => {
      const root = anAggregate();

      const updated = writeRows(root, [], 'embeddedComponents', [{ id: 'only' }]);

      expect(updated['embeddedComponents']).toEqual([{ id: 'only' }]);
      expect(root.embeddedComponents).toHaveLength(2);
    });

    it('rebuilds only the spine down to the owner, sharing untouched branches', () => {
      const root = anAggregate();

      const updated = writeRows(root, [{ attrName: 'embeddedComponents', index: 0 }], 'embeddedDetails', [{ id: 'detail_2' }]);

      const updatedRows = updated['embeddedComponents'] as Record<string, unknown>[];
      expect(updatedRows[0]['embeddedDetails']).toEqual([{ id: 'detail_2' }]);
      // The untouched sibling is the very same object, not a copy.
      expect(updatedRows[1]).toBe(root.embeddedComponents[1]);
      expect(root.embeddedComponents[0].embeddedDetails).toEqual([{ id: 'detail_1', name: 'detail one' }]);
    });

    it('names the missing hop when the path does not exist', () => {
      expect(() => writeRows(anAggregate(), [{ attrName: 'embeddedComponents', index: 7 }], 'embeddedDetails', [])).toThrowError(/embeddedComponents\[7\]/);
    });
  });

  describe('row identity', () => {
    const rows = [{ id: 'a', name: 'first' }, { id: 'b' }];

    it('finds a row by its id', () => {
      expect(findRow(rows, 'a')).toEqual({ id: 'a', name: 'first' });
      expect(indexOfRow(rows, 'b')).toBe(1);
    });

    it('reports a miss rather than guessing', () => {
      expect(indexOfRow(rows, 'missing')).toBe(-1);
      expect(indexOfRow(rows, '')).toBe(-1);
      expect(findRow(rows, 'missing')).toBeUndefined();
    });

    // App Region has no `id`; `type` is what identifies it. See APP_REGION_ID_FIELD.
    it('identifies a row by referenceIdField when it has no id', () => {
      const regions = [{ type: 'header' }, { type: 'sidenav' }];

      expect(rowId(regions[1], 'type')).toBe('sidenav');
      expect(indexOfRow(regions, 'sidenav', 'type')).toBe(1);
      expect(findRow(regions, 'header', 'type')).toEqual({ type: 'header' });
      // ...and is not found by the default id field, so a mis-declared attribute fails visibly.
      expect(indexOfRow(regions, 'sidenav')).toBe(-1);
    });

    it('yields an empty id for a row whose key field is unset', () => {
      expect(rowId({}, undefined)).toBe('');
      expect(rowId({ id: 'a' }, 'type')).toBe('');
    });
  });

  describe('row mutation', () => {
    const rows = [{ id: 'a' }, { id: 'b' }];

    it('appends without mutating', () => {
      expect(appendRow(rows, { id: 'c' })).toEqual([{ id: 'a' }, { id: 'b' }, { id: 'c' }]);
      expect(rows).toHaveLength(2);
    });

    it('replaces a row in place', () => {
      expect(replaceRow(rows, 'a', { id: 'a', name: 'changed' })).toEqual([{ id: 'a', name: 'changed' }, { id: 'b' }]);
    });

    /** An add saves a row the aggregate has not seen yet, so replace has to double as an insert. */
    it('appends when the row to replace is not there yet', () => {
      expect(replaceRow(rows, 'c', { id: 'c' })).toEqual([{ id: 'a' }, { id: 'b' }, { id: 'c' }]);
    });

    it('removes a row and leaves the array alone when the row is unknown', () => {
      expect(removeRow(rows, 'a')).toEqual([{ id: 'b' }]);
      expect(removeRow(rows, 'missing')).toEqual(rows);
      expect(rows).toHaveLength(2);
    });

    it('replaces and removes by referenceIdField', () => {
      const regions = [{ type: 'header' }, { type: 'sidenav' }];

      expect(replaceRow(regions, 'header', { type: 'header', title: 'x' }, 'type')).toEqual([{ type: 'header', title: 'x' }, { type: 'sidenav' }]);
      expect(removeRow(regions, 'sidenav', 'type')).toEqual([{ type: 'header' }]);
    });
  });
});
