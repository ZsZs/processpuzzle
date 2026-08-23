import { describe, expect, it } from 'vitest';
import { BeanRef, State, StateMachineDefinition, Transition } from './state-machine-definition';

describe('StateMachineDefinition model', () => {
  it('mirrors entityName onto id, so the generic screens can address a machine', () => {
    const definition = new StateMachineDefinition({ entityName: 'order' });

    expect(definition.id).toBe('order');
  });

  it('keeps an explicitly supplied id, which is what a loaded record carries', () => {
    const definition = new StateMachineDefinition({ id: 'order', entityName: 'order' });

    expect(definition.id).toBe('order');
  });

  it('starts a blank machine with empty state and transition lists, so an Add has something to append to', () => {
    const definition = new StateMachineDefinition();

    expect(definition.states).toEqual([]);
    expect(definition.transitions).toEqual([]);
    expect(definition.id).toBe('');
  });

  it('defaults a state to neither final nor locked', () => {
    const state = new State({ key: 'DRAFT', name: 'Draft' });

    expect(state.isFinal).toBe(false);
    expect(state.isLocked).toBe(false);
  });

  it('keeps the flags a final state was created with', () => {
    const state = new State({ key: 'DELIVERED', name: 'Delivered', isFinal: true, isLocked: true });

    expect(state.isFinal).toBe(true);
    expect(state.isLocked).toBe(true);
  });

  it('starts a transition with empty guard and action lists', () => {
    const transition = new Transition({ key: 'ship', sourceStateKey: 'CONFIRMED', targetStateKey: 'SHIPPED', triggerKey: 'ship' });

    expect(transition.guards).toEqual([]);
    expect(transition.actions).toEqual([]);
  });

  it('leaves a bean reference without params rather than inventing an empty map', () => {
    const beanRef = new BeanRef({ beanName: 'sufficientBalanceGuard' });

    expect(beanRef.beanName).toBe('sufficientBalanceGuard');
    expect(beanRef.params).toBeUndefined();
  });
});
