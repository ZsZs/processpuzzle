import { describe, expect, it } from 'vitest';
import { STATE_NODE_TYPE } from '../../../domain/modeler/graph/state-machine-graph';
import { State } from '../../../domain/state-machine-definition';
import { newStateData, nextStateKey, STATE_PALETTE_ITEMS, stateKind } from './state-palette-items';

describe('state palette items', () => {
  describe('STATE_PALETTE_ITEMS', () => {
    it('offers start, end and a plain state, in that order', () => {
      expect(STATE_PALETTE_ITEMS.map((item) => item.data.kind)).toEqual(['start', 'end', 'state']);
    });

    // The dropped node has to resolve to `StateNodeComponent`, the same as one built by the converter.
    it('draws every symbol through the state node template', () => {
      expect(STATE_PALETTE_ITEMS.map((item) => item.type)).toEqual([STATE_NODE_TYPE, STATE_NODE_TYPE, STATE_NODE_TYPE]);
    });
  });

  describe('nextStateKey', () => {
    it('starts at one for a machine with no states', () => {
      expect(nextStateKey([])).toBe('STATE_1');
    });

    it('skips the keys already in use', () => {
      expect(nextStateKey(['STATE_1', 'STATE_2'])).toBe('STATE_3');
    });

    // Off `states.length` this would answer STATE_2 and collide.
    it('fills a gap left by a deleted state', () => {
      expect(nextStateKey(['STATE_2', 'STATE_3'])).toBe('STATE_1');
    });

    it('ignores keys of another shape', () => {
      expect(nextStateKey(['DRAFT', 'DELIVERED'])).toBe('STATE_1');
    });
  });

  describe('stateKind', () => {
    it('reads back the symbol every palette item was built from', () => {
      const kinds = (['start', 'end', 'state'] as const).map((kind) => newStateData(kind, 'STATE_1'));

      expect(kinds.map((data) => stateKind(data.state, data.initial))).toEqual(['start', 'end', 'state']);
    });

    // A machine written by hand on the Details tab flags nothing, and reads as plain states.
    it('calls an unflagged state plain', () => {
      expect(stateKind(new State({ key: 'DRAFT' }), false)).toBe('state');
    });

    // Drawn as the entry point, since that is the more useful of the two facts about it.
    it('draws a state that both starts and ends the machine as the start', () => {
      expect(stateKind(new State({ key: 'ONLY', terminal: true }), true)).toBe('start');
    });
  });

  describe('newStateData', () => {
    it('names a state after the key it was minted under', () => {
      const data = newStateData('state', 'STATE_7');

      expect(data.state.key).toBe('STATE_7');
      expect(data.state.name).toBe('State 7');
      expect(data.label).toBe('State 7');
    });

    it('marks only the end symbol terminal', () => {
      expect(newStateData('end', 'STATE_1').state.terminal).toBe(true);
      expect(newStateData('start', 'STATE_1').state.terminal).toBe(false);
      expect(newStateData('state', 'STATE_1').state.terminal).toBe(false);
    });

    it('marks only the start symbol initial', () => {
      expect(newStateData('start', 'STATE_1').initial).toBe(true);
      expect(newStateData('end', 'STATE_1').initial).toBe(false);
      expect(newStateData('state', 'STATE_1').initial).toBe(false);
    });

    // ng-diagram spreads a palette item's `data` into every node it creates from it, so a shared `State`
    // would make two dropped states the same object.
    it('mints a state of its own each time', () => {
      expect(newStateData('state', 'STATE_1').state).not.toBe(newStateData('state', 'STATE_1').state);
    });
  });
});
