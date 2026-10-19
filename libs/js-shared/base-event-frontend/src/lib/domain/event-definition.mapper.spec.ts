import { describe, expect, it } from 'vitest';
import { EventAction, EventDefinition, EventKind, EventParameter, EventParameterType } from './event-definition';
import { EventDefinitionMapper } from './event-definition.mapper';
import { INVOICE_ISSUED_EVENT_DTO, ORDER_CONFIRMED_EVENT_DTO, ORDER_CREATED_EVENT_DTO } from './test-event-definition';

describe('EventDefinitionMapper', () => {
  const mapper = new EventDefinitionMapper();

  describe('fromDto', () => {
    it('reads every field of a seeded definition', () => {
      const entity = mapper.fromDto(ORDER_CREATED_EVENT_DTO);

      expect(entity).toBeInstanceOf(EventDefinition);
      expect(entity).toMatchObject({ id: 'OrderCreatedEvent', name: 'Order Created', kind: EventKind.SYSTEM, subjectType: 'order', action: EventAction.CREATED, version: 0 });
      expect(entity.description).toBe('An order entity object was created.');
      expect(entity.state).toBeUndefined();
    });

    it('reads the state of a STATE_CHANGED event', () => {
      expect(mapper.fromDto(ORDER_CONFIRMED_EVENT_DTO).state).toBe('CONFIRMED');
    });

    /** Reported by the backend on a draft; never sent back, since only a release changes it. */
    it('reads the release state, and does not send it back', () => {
      const entity = mapper.fromDto({ ...ORDER_CREATED_EVENT_DTO, releaseState: 'CHANGED' });

      expect(entity.releaseState).toBe('CHANGED');
      expect(mapper.toDto(entity)).not.toHaveProperty('releaseState');
    });

    it('defaults an absent kind to SYSTEM', () => {
      expect(mapper.fromDto({ id: 'x', name: 'X' }).kind).toBe(EventKind.SYSTEM);
    });

    it('reads the parameters as embedded entities', () => {
      const [order, invoice, invoiceNumber] = mapper.fromDto(INVOICE_ISSUED_EVENT_DTO).parameters;

      expect(order).toBeInstanceOf(EventParameter);
      expect(order).toMatchObject({ name: 'order', type: EventParameterType.ENTITY_REF, entityType: 'order', required: true });
      expect(invoice.entityType).toBe('document');
      expect(invoiceNumber).toMatchObject({ name: 'invoiceNumber', type: EventParameterType.STRING, required: true });
      expect(invoiceNumber.entityType).toBeUndefined();
    });

    it('reads a definition without parameters as an empty list, and the nulls of a parameter as absent', () => {
      expect(mapper.fromDto(ORDER_CONFIRMED_EVENT_DTO).parameters).toEqual([]);

      const [parameter] = mapper.fromDto({ id: 'x', name: 'X', parameters: [{ name: 'n', type: 'NUMBER', entityType: null, description: null }] }).parameters;

      expect(parameter.entityType).toBeUndefined();
      expect(parameter.description).toBeUndefined();
      expect(parameter.required).toBe(false);
    });
  });

  describe('toDto', () => {
    it('round-trips a seeded definition unchanged', () => {
      expect(mapper.toDto(mapper.fromDto(ORDER_CONFIRMED_EVENT_DTO))).toEqual({ ...ORDER_CONFIRMED_EVENT_DTO, description: undefined, parameters: [] });
      expect(mapper.toDto(mapper.fromDto(ORDER_CREATED_EVENT_DTO))).toEqual({ ...ORDER_CREATED_EVENT_DTO, state: undefined });
      expect(mapper.toDto(mapper.fromDto(INVOICE_ISSUED_EVENT_DTO))).toEqual({ ...INVOICE_ISSUED_EVENT_DTO, description: undefined });
    });

    // A full replacement: removing the last row has to reach the backend as an empty list.
    it('always sends the parameters, an empty list included', () => {
      expect(mapper.toDto(new EventDefinition({ id: 'e', name: 'n' })).parameters).toEqual([]);
    });

    // The form shows the entity type whatever the parameter's type, so a switched type may leave it filled in.
    it("drops a parameter's entity type unless it is an ENTITY_REF", () => {
      const entity = new EventDefinition({
        id: 'e',
        name: 'n',
        parameters: [
          new EventParameter({ name: 'orderNumber', type: EventParameterType.STRING, entityType: 'order' }),
          new EventParameter({ name: 'order', type: EventParameterType.ENTITY_REF, entityType: 'order' }),
          new EventParameter({ name: 'other', type: EventParameterType.ENTITY_REF, entityType: ' ' }),
        ],
      });

      expect(mapper.toDto(entity).parameters?.map((parameter) => parameter.entityType)).toEqual([undefined, 'order', undefined]);
    });

    it('sends an untouched checkbox as not required, and a cleared description as absent', () => {
      const parameter = new EventParameter({ name: 'n', description: '' });
      (parameter as unknown as { required: null }).required = null;

      const [dto] = mapper.toDto(new EventDefinition({ id: 'e', name: 'n', parameters: [parameter] })).parameters ?? [];

      expect(dto).toEqual({ name: 'n', type: 'STRING', entityType: undefined, required: false, description: undefined });
    });

    // The form shows the subject fields whatever the kind, so a switched kind may leave them filled in.
    it('drops the subject fields of a MESSAGE or SIGNAL event', () => {
      [EventKind.MESSAGE, EventKind.SIGNAL].forEach((kind) => {
        const dto = mapper.toDto(new EventDefinition({ id: 'Ping', name: 'Ping', kind, subjectType: 'order', action: EventAction.STATE_CHANGED, state: 'CONFIRMED' }));

        expect(dto.kind).toBe(kind);
        expect(dto.subjectType).toBeUndefined();
        expect(dto.action).toBeUndefined();
        expect(dto.state).toBeUndefined();
      });
    });

    it('drops the state unless the action is STATE_CHANGED', () => {
      const dto = mapper.toDto(new EventDefinition({ id: 'OrderUpdatedEvent', name: 'n', subjectType: 'order', action: EventAction.UPDATED, state: 'CONFIRMED' }));

      expect(dto.action).toBe(EventAction.UPDATED);
      expect(dto.state).toBeUndefined();
    });

    it('sends what a cleared text box writes back as absent', () => {
      const entity = new EventDefinition({ id: 'e', name: 'n', description: '  ', subjectType: '', action: EventAction.STATE_CHANGED, state: '' });
      const dto = mapper.toDto(entity);

      expect(dto.description).toBeUndefined();
      expect(dto.subjectType).toBeUndefined();
      expect(dto.state).toBeUndefined();
    });

    it('tolerates a null a form control may leave behind', () => {
      const entity = new EventDefinition({ id: 'e', name: 'n' });
      (entity as unknown as { subjectType: null }).subjectType = null;

      expect(mapper.toDto(entity).subjectType).toBeUndefined();
    });

    it('carries the version through, so a stale write can be refused', () => {
      expect(mapper.toDto(new EventDefinition({ id: 'e', name: 'n', version: 7 })).version).toBe(7);
      expect(mapper.toDto(new EventDefinition({ id: 'e', name: 'n' })).version).toBeUndefined();
    });
  });
});
