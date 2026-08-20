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

  it('defaults a state to neither terminal nor locked', () => {
    const state = new State({ key: 'DRAFT', name: 'Draft' });

    expect(state.terminal).toBe(false);
    expect(state.locked).toBe(false);
  });

  it('keeps the flags a terminal state was created with', () => {
    const state = new State({ key: 'DELIVERED', name: 'Delivered', terminal: true, locked: true });

    expect(state.terminal).toBe(true);
    expect(state.locked).toBe(true);
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
