import { describe, expect, it } from 'vitest';
import { DynamicEntityMapper } from './dynamic-entity.mapper';

const mapper = new DynamicEntityMapper('order');

describe('DynamicEntityMapper', () => {
  describe('fromDto()', () => {
    it('flattens the payload onto the entity, which is the only shape the generated form and table can bind to', () => {
      const entity = mapper.fromDto({
        id: 'b7e1',
        version: 3,
        entityDefinitionCode: 'order',
        payload: { orderNumber: 'ORD-1001', customerName: 'Alice Johnson', total: 89.88 },
      });

      expect(entity).toEqual({ id: 'b7e1', version: 3, orderNumber: 'ORD-1001', customerName: 'Alice Johnson', total: 89.88 });
    });

    it('keeps nested embedded rows as they arrived — the child store reads them out of this payload', () => {
      const entity = mapper.fromDto({ id: 'b7e1', version: 1, payload: { lineItems: [{ productName: 'Widget A', quantity: 2 }] } });

      expect(entity['lineItems']).toEqual([{ productName: 'Widget A', quantity: 2 }]);
    });

    /** A tenant may declare an attribute called `id`; losing the row's real id to it would make it unaddressable. */
    it('lets the envelope id and version win over payload attributes of the same name', () => {
      const entity = mapper.fromDto({ id: 'real', version: 7, payload: { id: 'authored', version: 99 } });

      expect(entity.id).toBe('real');
      expect(entity.version).toBe(7);
    });

    it('tolerates an envelope with no payload', () => {
      expect(mapper.fromDto({ id: 'b7e1', version: 1 })).toEqual({ id: 'b7e1', version: 1 });
    });

    // assertPersistedEntity in BaseEntityRestService is what turns this into a legible error.
    it('yields an empty entity for a response that is not an object, rather than throwing here', () => {
      expect(mapper.fromDto(null)).toEqual({});
      expect(mapper.fromDto('<html>')).toEqual({});
      expect(mapper.fromDto([])).toEqual({});
    });

    it('ignores an id or version of the wrong type instead of carrying it through', () => {
      expect(mapper.fromDto({ id: 42, version: '3', payload: { orderNumber: 'ORD-1' } })).toEqual({ orderNumber: 'ORD-1' });
    });
  });

  describe('toDto()', () => {
    it('re-nests the attributes under payload and names the definition, as the create endpoint requires', () => {
      expect(mapper.toDto({ orderNumber: 'ORD-1004', customerName: 'Dan' })).toEqual({
        entityDefinitionCode: 'order',
        payload: { orderNumber: 'ORD-1004', customerName: 'Dan' },
      });
    });

    it('sends the version alongside, as the update endpoint requires for optimistic locking', () => {
      expect(mapper.toDto({ id: 'b7e1', version: 3, orderNumber: 'ORD-1001' })).toEqual({
        entityDefinitionCode: 'order',
        version: 3,
        payload: { orderNumber: 'ORD-1001' },
      });
    });

    /** It is a path segment on update and the server's to assign on create, so a body carrying it could only disagree. */
    it('never sends the id', () => {
      expect(mapper.toDto({ id: 'b7e1', orderNumber: 'ORD-1001' })).not.toHaveProperty('id');
    });

    it('omits the version for a row that has none, so a create body carries no null lock', () => {
      expect(mapper.toDto({ orderNumber: 'ORD-1004' })).not.toHaveProperty('version');
    });

    it('round-trips an entity read from the backend back into the same payload', () => {
      const dto = { id: 'b7e1', version: 3, entityDefinitionCode: 'order', payload: { orderNumber: 'ORD-1001', lineItems: [{ productName: 'Widget A' }] } };

      expect(mapper.toDto(mapper.fromDto(dto))).toEqual({ entityDefinitionCode: 'order', version: 3, payload: dto.payload });
    });
  });
});
