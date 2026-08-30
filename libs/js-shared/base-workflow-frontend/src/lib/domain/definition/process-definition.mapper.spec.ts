import { describe, expect, it } from 'vitest';
import { ProcessDefinition, ProcessTaskAssignment } from './process-definition';
import { ProcessDefinitionMapper } from './process-definition.mapper';
import { PROCESS_DEFINITION_DTO } from './test-process-definition';

describe('ProcessDefinitionMapper', () => {
  const mapper = new ProcessDefinitionMapper();

  describe('fromDto', () => {
    const process = mapper.fromDto(PROCESS_DEFINITION_DTO);

    it('reads the header fields of the seeded process', () => {
      expect(process.id).toBe('order-fulfillment-workflow');
      expect(process.name).toBe('Order Fulfillment Workflow');
      expect(process.version).toBe(3);
      expect(process.activeInstances).toBe(1);
    });

    it('reads the three catalog references as id lists', () => {
      expect(process.roles).toEqual(['clerk', 'manager']);
      expect(process.artifacts).toEqual(['order-entity', 'fulfillment-invoice']);
      expect(process.tools).toEqual(['automated-check-tool']);
    });

    it('maps every assignment rather than passing the list through', () => {
      expect(process.tasks).toHaveLength(3);
      expect(process.tasks[0]).toBeInstanceOf(ProcessTaskAssignment);
      expect(process.tasks[0].taskDefinitionId).toBe('review-order');
      expect(process.tasks[0].performedBy).toBe('clerk');
      expect(process.tasks[1].dependsOn).toEqual(['review-order']);
    });

    // Applied on the way *in* as well as out, so a payload holding embedded documents — as the
    // pre-catalog contract answered with — loads as ids rather than half-flattening on the next save.
    it('flattens a reference list that arrived as whole entities', () => {
      const embedded = mapper.fromDto({ id: 'p1', roles: [{ id: 'clerk', name: 'Order Clerk' }], artifacts: [{ id: 'order-entity' }] });

      expect(embedded.roles).toEqual(['clerk']);
      expect(embedded.artifacts).toEqual(['order-entity']);
    });

    it('defaults a list the document omits, so no control gets undefined', () => {
      const bare = mapper.fromDto({ id: 'p1', name: 'P1' });

      expect(bare.roles).toEqual([]);
      expect(bare.artifacts).toEqual([]);
      expect(bare.tools).toEqual([]);
      expect(bare.tasks).toEqual([]);
    });
  });

  describe('toDto', () => {
    it('round-trips the seeded process without losing a reference or an assignment', () => {
      const dto = mapper.toDto(mapper.fromDto(PROCESS_DEFINITION_DTO));

      expect(dto.id).toBe('order-fulfillment-workflow');
      expect(dto.roles).toEqual(['clerk', 'manager']);
      expect(dto.artifacts).toEqual(['order-entity', 'fulfillment-invoice']);
      expect(dto.tools).toEqual(['automated-check-tool']);
      expect(dto.tasks).toHaveLength(3);
      expect(dto.tasks?.[2]).toMatchObject({ taskDefinitionId: 'confirm-delivery', performedBy: 'clerk' });
    });

    // The save path of the `RELATED_ENTITIES` control: the user picked a role from its own list, so the
    // control put the whole record in the form value, and the contract wants the id.
    it('flattens the entities the reference controls wrote on selection', () => {
      const process = new ProcessDefinition({ id: 'p1' });
      (process as unknown as Record<string, unknown>)['roles'] = ['clerk', { id: 'manager', name: 'Order Manager' }];

      expect(mapper.toDto(process).roles).toEqual(['clerk', 'manager']);
    });

    // `PUT /processes/{processId}` is a full replacement, so an absent list is an emptied one. A blank
    // process therefore has to send four empty arrays rather than four missing keys.
    it('emits every list unconditionally', () => {
      const dto = mapper.toDto(new ProcessDefinition({ id: 'p1', name: 'P1' }));

      expect(dto.roles).toEqual([]);
      expect(dto.artifacts).toEqual([]);
      expect(dto.tools).toEqual([]);
      expect(dto.tasks).toEqual([]);
    });

    it('spells an unticked checkbox as false rather than omitting it', () => {
      const dto = mapper.toDto(new ProcessDefinition({ tasks: [new ProcessTaskAssignment({ taskDefinitionId: 't1' })] }));

      expect(dto.tasks?.[0].parallel).toBe(false);
      expect(dto.tasks?.[0].override).toBe(false);
    });

    // Server-computed and marked read-only in the contract: the backend recounts it per list row, so
    // sending it back would be sending a derived value it is about to overwrite.
    it('never sends activeInstances back', () => {
      const dto = mapper.toDto(mapper.fromDto(PROCESS_DEFINITION_DTO));

      expect('activeInstances' in dto).toBe(false);
    });

    // Listed field by field rather than spread, so a control the form may gain later cannot leak.
    it('emits exactly the contract’s fields and nothing else', () => {
      const dto = mapper.toDto(mapper.fromDto(PROCESS_DEFINITION_DTO));

      expect(Object.keys(dto).sort()).toEqual(['artifacts', 'createdAt', 'description', 'extends', 'id', 'name', 'roles', 'tasks', 'tools', 'updatedAt', 'version']);
    });
  });
});
