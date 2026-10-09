import { describe, expect, it } from 'vitest';
import { EventAction, EventDefinition, EventKind } from './event-definition';
import { EventDefinitionMapper } from './event-definition.mapper';
import { ORDER_CONFIRMED_EVENT_DTO, ORDER_CREATED_EVENT_DTO } from './test-event-definition';

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

    it('defaults an absent kind to SYSTEM', () => {
      expect(mapper.fromDto({ id: 'x', name: 'X' }).kind).toBe(EventKind.SYSTEM);
    });
  });

  describe('toDto', () => {
    it('round-trips a seeded definition unchanged', () => {
      expect(mapper.toDto(mapper.fromDto(ORDER_CONFIRMED_EVENT_DTO))).toEqual({ ...ORDER_CONFIRMED_EVENT_DTO, description: undefined });
      expect(mapper.toDto(mapper.fromDto(ORDER_CREATED_EVENT_DTO))).toEqual({ ...ORDER_CREATED_EVENT_DTO, state: undefined });
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
