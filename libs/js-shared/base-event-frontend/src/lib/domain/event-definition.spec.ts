import { describe, expect, it } from 'vitest';
import { EventAction, EventDefinition, EventKind, EventParameter, EventParameterType } from './event-definition';

describe('EventDefinition model', () => {
  it('starts a blank definition as a SYSTEM event with no subject', () => {
    const definition = new EventDefinition();

    expect(definition.id).toBe('');
    expect(definition.name).toBe('');
    expect(definition.kind).toBe(EventKind.SYSTEM);
    expect(definition.subjectType).toBeUndefined();
    expect(definition.action).toBeUndefined();
    expect(definition.state).toBeUndefined();
    expect(definition.parameters).toEqual([]);
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

  it('keeps the parameters it was created with', () => {
    const parameter = new EventParameter({ name: 'invoiceNumber', required: true });

    expect(new EventDefinition({ parameters: [parameter] }).parameters).toEqual([parameter]);
  });
});

describe('EventParameter model', () => {
  // The blank row an `Add` opens the form on: the contract defaults `required` to false.
  it('starts a blank parameter as an optional STRING', () => {
    const parameter = new EventParameter();

    expect(parameter.name).toBe('');
    expect(parameter.type).toBe(EventParameterType.STRING);
    expect(parameter.required).toBe(false);
    expect(parameter.entityType).toBeUndefined();
    expect(parameter.description).toBeUndefined();
  });

  // Identified by its name, so the row carries no id of its own.
  it('keeps every field it was created with, and no id', () => {
    const parameter = new EventParameter({ name: 'order', type: EventParameterType.ENTITY_REF, entityType: 'order', required: true, description: 'd' });

    expect(parameter).toMatchObject({ name: 'order', type: 'ENTITY_REF', entityType: 'order', required: true, description: 'd' });
    expect(parameter.id).toBeUndefined();
  });

  it('offers the five types the contract defines', () => {
    expect(Object.keys(EventParameterType)).toEqual(['STRING', 'NUMBER', 'BOOLEAN', 'DATE', 'ENTITY_REF']);
  });
});
