import { describe, expect, it } from 'vitest';
import { StateMachineDefinition } from './state-machine-definition';
import { StateMachineDefinitionMapper } from './state-machine-definition.mapper';
import { OTHER_STATE_MACHINE_DEFINITION_DTO, STATE_MACHINE_DEFINITION_DTO } from './test-state-machine-definition';

describe('StateMachineDefinitionMapper', () => {
  const mapper = new StateMachineDefinitionMapper();

  describe('fromDto', () => {
    it('mirrors entityName onto id, the contract giving a machine no key of its own', () => {
      const entity = mapper.fromDto(STATE_MACHINE_DEFINITION_DTO);

      expect(entity.id).toBe('order');
      expect(entity.entityName).toBe('order');
    });

    it('falls back to id when a record carries one but no entityName, as a json-server row may', () => {
      const entity = mapper.fromDto({ id: 'order', name: 'Order State Machine' });

      expect(entity.entityName).toBe('order');
    });

    it('carries the whole graph, so the edit form does not read a projection', () => {
      const entity = mapper.fromDto(STATE_MACHINE_DEFINITION_DTO);

      expect(entity.stateAttributeKey).toBe('status');
      expect(entity.initialStateKey).toBe('DRAFT');
      expect(entity.states).toHaveLength(2);
      expect(entity.transitions).toHaveLength(1);
      expect(entity.transitions[0].guards[0].beanName).toBe('sufficientBalanceGuard');
      expect(entity.transitions[0].guards[0].params).toEqual({ threshold: '100' });
      expect(entity.transitions[0].actions[0].beanName).toBe('sendApprovalNotificationAction');
      expect(entity.version).toBe(3);
      expect(entity.orgKey).toBe('processpuzzle-testbed');
    });

    it("reads the contract's terminal and locked flags", () => {
      const entity = mapper.fromDto(STATE_MACHINE_DEFINITION_DTO);

      expect(entity.states[1].terminal).toBe(true);
      expect(entity.states[1].locked).toBe(true);
      expect(entity.states[1].metadata).toEqual({ colour: 'green' });
    });

    // The seed YAML both backends are provisioned from spells these `isFinal` / `isLocked`, and
    // json-server serves that YAML verbatim. Left unnormalized, the state form would show an unticked
    // Terminal box for a terminal state and drop the flag on the next save.
    it("also reads the seed YAML's isFinal and isLocked spelling", () => {
      const entity = mapper.fromDto(OTHER_STATE_MACHINE_DEFINITION_DTO);

      expect(entity.states[1].terminal).toBe(true);
      expect(entity.states[1].locked).toBe(true);
      expect(entity.states[0].terminal).toBe(false);
    });

    it('turns a machine with no transitions into empty lists rather than undefined', () => {
      const entity = mapper.fromDto({ entityName: 'order', name: 'Order' });

      expect(entity.states).toEqual([]);
      expect(entity.transitions).toEqual([]);
    });
  });

  describe('toDto', () => {
    it('sends entityName and mirrors it onto id, which json-server keys the record by', () => {
      const dto = mapper.toDto(mapper.fromDto(STATE_MACHINE_DEFINITION_DTO));

      expect(dto.entityName).toBe('order');
      expect(dto.id).toBe('order');
    });

    it('falls back to id when only the mirror was ever set', () => {
      const dto = mapper.toDto(new StateMachineDefinition({ id: 'order', name: 'Order' }));

      expect(dto.entityName).toBe('order');
    });

    // PUT is a full replacement, so a missing list is an emptied machine rather than an untouched one.
    it('always sends the states and transitions, even for an empty machine', () => {
      const dto = mapper.toDto(new StateMachineDefinition({ entityName: 'order' }));

      expect(dto.states).toEqual([]);
      expect(dto.transitions).toEqual([]);
    });

    it("writes only the contract's flag spelling, so the two cannot disagree later", () => {
      const dto = mapper.toDto(mapper.fromDto(OTHER_STATE_MACHINE_DEFINITION_DTO));

      expect(dto.states?.[1]).toEqual({ key: 'ARCHIVED', name: 'Archived', description: undefined, terminal: true, locked: true, metadata: undefined });
    });

    it('round-trips the guards and actions of a transition', () => {
      const dto = mapper.toDto(mapper.fromDto(STATE_MACHINE_DEFINITION_DTO));

      expect(dto.transitions?.[0].guards).toEqual([{ beanName: 'sufficientBalanceGuard', params: { threshold: '100' } }]);
      expect(dto.transitions?.[0].actions).toEqual([{ beanName: 'sendApprovalNotificationAction', params: undefined }]);
    });

    // The rows of a loaded machine are the parsed JSON the embedded controls edit in place, not
    // instances of the model classes — so a row may reach the mapper without the flags at all.
    it('defaults the flags of a raw row rather than sending undefined', () => {
      const entity = new StateMachineDefinition({ entityName: 'order' });
      entity.states = [{ key: 'DRAFT', name: 'Draft' }] as StateMachineDefinition['states'];

      const dto = mapper.toDto(entity);

      expect(dto.states?.[0].terminal).toBe(false);
      expect(dto.states?.[0].locked).toBe(false);
    });

    it('tolerates a raw transition row whose guard and action lists are absent', () => {
      const entity = new StateMachineDefinition({ entityName: 'order' });
      entity.transitions = [{ key: 'ship', sourceStateKey: 'CONFIRMED', targetStateKey: 'SHIPPED', triggerKey: 'ship' }] as StateMachineDefinition['transitions'];

      const dto = mapper.toDto(entity);

      expect(dto.transitions?.[0].guards).toEqual([]);
      expect(dto.transitions?.[0].actions).toEqual([]);
    });
  });
});
