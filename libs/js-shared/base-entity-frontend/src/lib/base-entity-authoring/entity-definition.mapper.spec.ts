import { describe, expect, it } from 'vitest';
import { EntityAttributeDefinition, EntityDefinition } from '../base-entity-definition/entity-definition';
import { EntityDefinitionMapper } from './entity-definition.mapper';

describe('EntityDefinitionMapper', () => {
  const mapper = new EntityDefinitionMapper();

  const responseDto = {
    id: '8b1f3c9e-0000-4000-8000-000000000001',
    code: 'order',
    name: 'Order',
    description: 'A customer order',
    status: 'ACTIVE',
    componentParents: [],
    isEmbedded: false,
    version: 3,
    createdAt: '2026-01-02T03:04:05Z',
    updatedAt: '2026-02-03T04:05:06Z',
    attributes: [
      {
        id: '8b1f3c9e-0000-4000-8000-000000000002',
        code: 'orderNumber',
        name: 'Order #',
        displayOrder: 1,
        valueKind: 'TEXT',
        formControlType: 'TEXT_BOX',
        required: true,
        indexed: true,
        isLinkToDetails: true,
      },
    ],
  };

  describe('fromDto', () => {
    it('mirrors the code into the id the generated screens address a record by', () => {
      expect(mapper.fromDto(responseDto).id).toBe('order');
    });

    /** The contract's read-only uuid is dropped, because no request body has a field to send it back in. */
    it('does not carry the server-assigned uuid', () => {
      expect(mapper.fromDto(responseDto).id).not.toBe(responseDto.id);
    });

    it('reads every authored field of the definition', () => {
      const definition = mapper.fromDto(responseDto);

      expect(definition).toMatchObject({
        code: 'order',
        name: 'Order',
        description: 'A customer order',
        status: 'ACTIVE',
        componentParents: [],
        isEmbedded: false,
        version: 3,
        createdAt: '2026-01-02T03:04:05Z',
        updatedAt: '2026-02-03T04:05:06Z',
      });
    });

    /**
     * Element by element rather than passed through: a field the wire spelled differently would otherwise
     * leave its control empty and be dropped by the next save.
     */
    it('maps each attribute row', () => {
      const attributes = mapper.fromDto(responseDto).attributes ?? [];

      expect(attributes).toHaveLength(1);
      expect(attributes[0]).toBeInstanceOf(EntityAttributeDefinition);
      expect(attributes[0]).toMatchObject({ code: 'orderNumber', name: 'Order #', displayOrder: 1, valueKind: 'TEXT', formControlType: 'TEXT_BOX', required: true, indexed: true, isLinkToDetails: true });
    });

    it('falls back to the id when a response names no code', () => {
      expect(mapper.fromDto({ id: 'order-line', name: 'Order Line' }).code).toBe('order-line');
    });

    it('answers an attribute-less definition with an empty list rather than undefined', () => {
      expect(mapper.fromDto({ code: 'order', name: 'Order' }).attributes).toEqual([]);
    });
  });

  describe('toDto', () => {
    const definition = new EntityDefinition({
      code: 'order-line',
      name: 'Order Line',
      description: 'One line of an order',
      status: 'DRAFT',
      componentParents: ['order'],
      isEmbedded: true,
      version: 7,
      createdAt: '2026-01-02T03:04:05Z',
      updatedAt: '2026-02-03T04:05:06Z',
      attributes: [new EntityAttributeDefinition({ code: 'productName', name: 'Product', valueKind: 'TEXT', formControlType: 'TEXT_BOX', required: true })],
    });

    it('sends the authored fields of BaseEntityDefinitionInput', () => {
      expect(mapper.toDto(definition)).toMatchObject({
        code: 'order-line',
        name: 'Order Line',
        description: 'One line of an order',
        status: 'DRAFT',
        componentParents: ['order'],
        isEmbedded: true,
      });
    });

    /**
     * `BaseEntityDefinitionInput` has neither an `id` nor the three `readOnly` fields, and the path segment
     * is the sole source of truth for which definition is being replaced.
     */
    it('sends neither the id mirror nor a server-assigned field', () => {
      const dto = mapper.toDto(definition);

      expect(dto).not.toHaveProperty('id');
      expect(dto).not.toHaveProperty('version');
      expect(dto).not.toHaveProperty('createdAt');
      expect(dto).not.toHaveProperty('updatedAt');
    });

    it('falls back to the id mirror when the code was cleared on the form', () => {
      expect(mapper.toDto(new EntityDefinition({ id: 'order', code: '' })).code).toBe('order');
    });

    /** The PUT is a full replacement, so an absent list would empty the definition rather than leave it. */
    it('always sends the attributes, the parents and the embedded flag', () => {
      const dto = mapper.toDto(new EntityDefinition({ code: 'order', name: 'Order' }));

      expect(dto.attributes).toEqual([]);
      expect(dto.componentParents).toEqual([]);
      expect(dto.isEmbedded).toBe(false);
    });

    /** An unticked checkbox has to say so, for the same reason: an absent flag is an unset one. */
    it('sends every attribute flag explicitly, false included', () => {
      const dto = mapper.toDto(definition);

      expect(dto.attributes?.[0]).toMatchObject({ required: true, isMultiValued: false, indexed: false, isLinkToDetails: false });
    });

    /** Neither is part of `BaseEntityAttributeInput` — see the model and `fromAttribute`. */
    it('sends neither an attribute id nor an attribute description', () => {
      const attribute = mapper.toDto(definition).attributes?.[0];

      expect(attribute).not.toHaveProperty('id');
      expect(attribute).not.toHaveProperty('description');
    });
  });

  it('round-trips a definition through both directions', () => {
    const roundTripped = mapper.fromDto({ ...responseDto, ...mapper.toDto(mapper.fromDto(responseDto)) });

    expect(roundTripped.code).toBe('order');
    expect(roundTripped.attributes?.[0].code).toBe('orderNumber');
  });
});
