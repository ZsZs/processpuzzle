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
    releaseState: 'CHANGED',
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
        autosizeColumn: true,
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
        releaseState: 'CHANGED',
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
      expect(attributes[0]).toMatchObject({ code: 'orderNumber', name: 'Order #', displayOrder: 1, valueKind: 'TEXT', formControlType: 'TEXT_BOX', required: true, indexed: true, isLinkToDetails: true, autosizeColumn: true });
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
      status: 'DEPRECATED',
      releaseState: 'RELEASED',
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
        status: 'DEPRECATED',
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
      expect(dto).not.toHaveProperty('releaseState');
    });

    /** Not authored in the form, but a type of a common must stay shared when the common saves it. */
    it('carries the natural keys of a type through a save', () => {
      const identifiers = [['country', 'name']];

      expect(mapper.toDto(mapper.fromDto({ ...responseDto, identifiers })).identifiers).toEqual(identifiers);
    });

    it('shows the layout as one line per row and saves the edited text as rows', () => {
      const layout = [['a', 'b'], ['c']];
      const entity = mapper.fromDto({ ...responseDto, layout });

      expect(entity.layoutText).toBe('a, b\nc');
      expect(mapper.toDto(entity).layout).toEqual(layout);
      expect(mapper.toDto({ ...entity, layoutText: ' x ,y,\n\n z \n' }).layout).toEqual([['x', 'y'], ['z']]);
    });

    it('clears the layout when its text is emptied, and keeps it when there is no text', () => {
      const entity = mapper.fromDto({ ...responseDto, layout: [['a']] });

      expect(mapper.toDto({ ...entity, layoutText: '' }).layout).toBeUndefined();
      expect(mapper.toDto({ ...entity, layoutText: undefined }).layout).toEqual([['a']]);
    });

    it('carries the sharing of a type through a save, and never sends where it was shared from', () => {
      const sharing = { write: 'COMMON_ADMIN' as const, visibility: 'PUBLIC' as const };
      const read = mapper.fromDto({ ...responseDto, sharing, sharedFrom: 'starter-common' });

      expect(read.sharing).toEqual(sharing);
      expect(read.sharedFrom).toBe('starter-common');
      expect(mapper.toDto(read).sharing).toEqual(sharing);
      expect(mapper.toDto(read)).not.toHaveProperty('sharedFrom');
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

      expect(dto.attributes?.[0]).toMatchObject({ required: true, indexed: false, isLinkToDetails: false, autosizeColumn: false });
    });

    it('sends multiplicity explicitly or not at all, and maxOccurs only for the x forms', () => {
      const attributeDto = (init: Partial<EntityAttributeDefinition>) =>
        mapper.toDto(new EntityDefinition({ code: 'order', attributes: [new EntityAttributeDefinition({ code: 'files', ...init })] })).attributes?.[0];

      expect(attributeDto({})).not.toHaveProperty('multiplicity');
      expect(attributeDto({ multiplicity: '' as never })).not.toHaveProperty('multiplicity');
      expect(attributeDto({ multiplicity: '0..n', maxOccurs: 3 })).toMatchObject({ multiplicity: '0..n' });
      expect(attributeDto({ multiplicity: '0..n', maxOccurs: 3 })).not.toHaveProperty('maxOccurs');
      expect(attributeDto({ multiplicity: '1..x', maxOccurs: '4' as never })).toMatchObject({ multiplicity: '1..x', maxOccurs: 4 });
      expect(attributeDto({ multiplicity: '0..x', maxOccurs: '' as never })).not.toHaveProperty('maxOccurs');
    });

    it('reads multiplicity and maxOccurs back', () => {
      const definition = mapper.fromDto({ code: 'order', attributes: [{ code: 'files', multiplicity: '0..x', maxOccurs: 2 }] });

      expect(definition.attributes?.[0]).toMatchObject({ multiplicity: '0..x', maxOccurs: 2 });
    });

    /** Neither is part of `BaseEntityAttributeInput` — see the model and `fromAttribute`. */
    it('sends neither an attribute id nor an attribute description', () => {
      const attribute = mapper.toDto(definition).attributes?.[0];

      expect(attribute).not.toHaveProperty('id');
      expect(attribute).not.toHaveProperty('description');
    });
  });

  describe('ADDRESS map options', () => {
    const toDtoAttribute = (init: Partial<EntityAttributeDefinition>) =>
      mapper.toDto(new EntityDefinition({ code: 'customer', attributes: [new EntityAttributeDefinition({ code: 'address', ...init })] })).attributes?.[0];

    it('reads them from the response', () => {
      const [attribute] = mapper.fromDto({ code: 'customer', attributes: [{ code: 'address', formControlType: 'ADDRESS', showMap: true, mapProvider: 'GOOGLE', defaultCountry: 'HU' }] }).attributes ?? [];
      expect(attribute).toMatchObject({ showMap: true, mapProvider: 'GOOGLE', defaultCountry: 'HU' });
    });

    it('writes them for an ADDRESS attribute, the country upper-cased and showMap explicit', () => {
      expect(toDtoAttribute({ formControlType: 'ADDRESS', showMap: true, mapProvider: 'GOOGLE', defaultCountry: ' hu ' })).toMatchObject({ showMap: true, mapProvider: 'GOOGLE', defaultCountry: 'HU' });
      expect(toDtoAttribute({ formControlType: 'ADDRESS' })).toMatchObject({ showMap: false });
    });

    it('drops the provider without a map, a blank country, and every option on another control', () => {
      const withoutMap = toDtoAttribute({ formControlType: 'ADDRESS', mapProvider: 'GOOGLE', defaultCountry: ' ' });
      expect(withoutMap).not.toHaveProperty('mapProvider');
      expect(withoutMap).not.toHaveProperty('defaultCountry');
      const text = toDtoAttribute({ formControlType: 'TEXT', showMap: true, mapProvider: 'GOOGLE', defaultCountry: 'HU' });
      expect(text).not.toHaveProperty('showMap');
      expect(text).not.toHaveProperty('mapProvider');
      expect(text).not.toHaveProperty('defaultCountry');
    });
  });

  describe('measure options', () => {
    const toDtoAttribute = (init: Partial<EntityAttributeDefinition>) =>
      mapper.toDto(new EntityDefinition({ code: 'item', attributes: [new EntityAttributeDefinition({ code: 'size', ...init })] })).attributes?.[0];

    it('reads them from the response, a JSON null being no option', () => {
      const [attribute] =
        mapper.fromDto({
          code: 'item',
          attributes: [{ code: 'weight', formControlType: 'QUANTITY', quantityKind: 'MASS', allowedUnits: ['kg', 'g'], defaultUnit: 'kg', scale: 3, dimensionCount: 3, allowedCurrencies: ['EUR'], defaultCurrency: 'EUR' }],
        }).attributes ?? [];
      expect(attribute).toMatchObject({ quantityKind: 'MASS', allowedUnits: ['kg', 'g'], defaultUnit: 'kg', scale: 3, dimensionCount: 3, allowedCurrencies: ['EUR'], defaultCurrency: 'EUR' });

      const [empty] = mapper.fromDto({ code: 'item', attributes: [{ code: 'price', formControlType: 'MONEY', quantityKind: null, scale: null, allowedUnits: null } as never] }).attributes ?? [];
      expect(empty.quantityKind).toBeUndefined();
      expect(empty.scale).toBeUndefined();
      expect(empty.allowedUnits).toBeUndefined();
    });

    it('writes a QUANTITY its kind, units and scale — and nothing of MONEY or DIMENSIONS', () => {
      const dto = toDtoAttribute({ formControlType: 'QUANTITY', quantityKind: ' MASS ', allowedUnits: ['kg', 'g'], defaultUnit: 'kg', scale: '3' as never, allowedCurrencies: ['EUR'], defaultCurrency: 'EUR', dimensionCount: 3 });

      expect(dto).toMatchObject({ quantityKind: 'MASS', allowedUnits: ['kg', 'g'], defaultUnit: 'kg', scale: 3 });
      for (const option of ['allowedCurrencies', 'defaultCurrency', 'dimensionCount']) expect(dto).not.toHaveProperty(option);
    });

    it('writes DIMENSIONS its units, scale and axis count, but no quantity kind', () => {
      const dto = toDtoAttribute({ formControlType: 'DIMENSIONS', quantityKind: 'LENGTH', allowedUnits: ['cm'], defaultUnit: 'cm', scale: 1, dimensionCount: '3' as never });

      expect(dto).toMatchObject({ allowedUnits: ['cm'], defaultUnit: 'cm', scale: 1, dimensionCount: 3 });
      expect(dto).not.toHaveProperty('quantityKind');
    });

    it('writes MONEY its currencies, and none of the unit options', () => {
      const dto = toDtoAttribute({ formControlType: 'MONEY', allowedCurrencies: ['EUR', 'HUF'], defaultCurrency: ' HUF ', allowedUnits: ['kg'], scale: 2 });

      expect(dto).toMatchObject({ allowedCurrencies: ['EUR', 'HUF'], defaultCurrency: 'HUF' });
      for (const option of ['allowedUnits', 'scale', 'quantityKind', 'dimensionCount']) expect(dto).not.toHaveProperty(option);
    });

    it('leaves out what the form left empty — blank text, an empty list, a number that is not one', () => {
      const dto = toDtoAttribute({ formControlType: 'DIMENSIONS', allowedUnits: [], defaultUnit: '  ', scale: '' as never, dimensionCount: 'x' as never });

      for (const option of ['allowedUnits', 'defaultUnit', 'scale', 'dimensionCount']) expect(dto).not.toHaveProperty(option);
      expect(toDtoAttribute({ formControlType: 'MONEY', allowedCurrencies: [], defaultCurrency: '' })).not.toHaveProperty('allowedCurrencies');
      expect(toDtoAttribute({ formControlType: 'QUANTITY', scale: null as never })).not.toHaveProperty('scale');
    });

    it('writes none of them for any other control', () => {
      const dto = toDtoAttribute({ formControlType: 'TEXT', quantityKind: 'MASS', allowedUnits: ['kg'], defaultUnit: 'kg', scale: 2, allowedCurrencies: ['EUR'], defaultCurrency: 'EUR', dimensionCount: 3 });

      for (const option of ['quantityKind', 'allowedUnits', 'defaultUnit', 'scale', 'allowedCurrencies', 'defaultCurrency', 'dimensionCount']) expect(dto).not.toHaveProperty(option);
    });
  });

  it('round-trips a definition through both directions', () => {
    const roundTripped = mapper.fromDto({ ...responseDto, ...mapper.toDto(mapper.fromDto(responseDto)) });

    expect(roundTripped.code).toBe('order');
    expect(roundTripped.attributes?.[0].code).toBe('orderNumber');
  });

  describe('dateFormat', () => {
    const definitionWith = (attribute: Partial<EntityAttributeDefinition>) =>
      new EntityDefinition({ code: 'person', name: 'Person', attributes: [new EntityAttributeDefinition({ code: 'born', formControlType: 'DATE', ...attribute })] });

    it('unfolds a received dateFormat into the flat fields the form edits', () => {
      const definition = mapper.fromDto({ code: 'person', attributes: [{ code: 'born', valueKind: 'DATE_TIME', formControlType: 'DATE_TIME', dateFormat: { dateStyle: 'short', timeStyle: 'medium' } }] });

      expect(definition.attributes?.[0]).toMatchObject({ dateFormat: { dateStyle: 'short', timeStyle: 'medium' }, dateStyle: 'short', timeStyle: 'medium' });
    });

    it('folds the edited styles back into dateFormat, the stale nested value notwithstanding', () => {
      const dto = mapper.toDto(definitionWith({ valueKind: 'DATE_TIME', dateFormat: { dateStyle: 'short' }, dateStyle: 'long', timeStyle: 'short' }));

      expect(dto.attributes?.[0].dateFormat).toEqual({ dateStyle: 'long', timeStyle: 'short' });
      expect(dto.attributes?.[0]).not.toHaveProperty('dateStyle');
    });

    it('drops the time style of a DATE attribute and the whole format of a non-date one', () => {
      expect(mapper.toDto(definitionWith({ valueKind: 'DATE', dateStyle: 'short', timeStyle: 'short' })).attributes?.[0].dateFormat).toEqual({ dateStyle: 'short' });
      expect(mapper.toDto(definitionWith({ valueKind: 'TEXT', dateStyle: 'short' })).attributes?.[0].dateFormat).toBeUndefined();
    });

    it('sends no dateFormat when neither style is picked, so the value kind default applies', () => {
      expect(mapper.toDto(definitionWith({ valueKind: 'DATE_TIME' })).attributes?.[0].dateFormat).toBeUndefined();
    });
  });
});
