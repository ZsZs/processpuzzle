import { describe, expect, it } from 'vitest';
import { EventAction, EventDefinition, EventKind } from './event-definition';

describe('EventDefinition model', () => {
  it('starts a blank definition as a SYSTEM event with no subject', () => {
    const definition = new EventDefinition();

    expect(definition.id).toBe('');
    expect(definition.name).toBe('');
    expect(definition.kind).toBe(EventKind.SYSTEM);
    expect(definition.subjectType).toBeUndefined();
    expect(definition.action).toBeUndefined();
    expect(definition.state).toBeUndefined();
    expect(definition.version).toBeUndefined();
  });

  it('keeps every field it was created with', () => {
    const definition = new EventDefinition({
      id: 'OrderConfirmedEvent',
      name: 'Order Confirmed',
      description: 'd',
      kind: EventKind.SYSTEM,
      subjectType: 'order',
      action: EventAction.STATE_CHANGED,
      state: 'CONFIRMED',
      version: 2,
    });

    expect(definition).toMatchObject({ id: 'OrderConfirmedEvent', name: 'Order Confirmed', description: 'd', subjectType: 'order', action: 'STATE_CHANGED', state: 'CONFIRMED', version: 2 });
  });

  it('offers the three kinds and four actions the contract defines', () => {
    expect(Object.keys(EventKind)).toEqual(['SYSTEM', 'MESSAGE', 'SIGNAL']);
    expect(Object.keys(EventAction)).toEqual(['CREATED', 'UPDATED', 'DELETED', 'STATE_CHANGED']);
  });
});
