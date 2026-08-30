import { describe, expect, it } from 'vitest';
import { Workflow, WorkflowTaskAssignment } from './workflow';
import { WorkflowMapper } from './workflow.mapper';
import { WORKFLOW_DTO } from './test-workflow';

describe('WorkflowMapper', () => {
  const mapper = new WorkflowMapper();

  describe('fromDto', () => {
    const workflow = mapper.fromDto(WORKFLOW_DTO);

    it('reads the header fields of the seeded workflow', () => {
      expect(workflow.id).toBe('order-fulfillment-workflow');
      expect(workflow.name).toBe('Order Fulfillment Workflow');
      expect(workflow.version).toBe(3);
      expect(workflow.activeInstances).toBe(1);
    });

    it('reads the three catalog references as id lists', () => {
      expect(workflow.roles).toEqual(['clerk', 'manager']);
      expect(workflow.artifacts).toEqual(['order-entity', 'fulfillment-invoice']);
      expect(workflow.tools).toEqual(['automated-check-tool']);
    });

    it('maps every assignment rather than passing the list through', () => {
      expect(workflow.tasks).toHaveLength(3);
      expect(workflow.tasks[0]).toBeInstanceOf(WorkflowTaskAssignment);
      expect(workflow.tasks[0].taskDefinitionId).toBe('review-order');
      expect(workflow.tasks[0].performedBy).toBe('clerk');
      expect(workflow.tasks[1].dependsOn).toEqual(['review-order']);
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
    it('round-trips the seeded workflow without losing a reference or an assignment', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

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
      const workflow = new Workflow({ id: 'p1' });
      (workflow as unknown as Record<string, unknown>)['roles'] = ['clerk', { id: 'manager', name: 'Order Manager' }];

      expect(mapper.toDto(workflow).roles).toEqual(['clerk', 'manager']);
    });

    // `PUT /workflows/{workflowId}` is a full replacement, so an absent list is an emptied one. A blank
    // workflow therefore has to send four empty arrays rather than four missing keys.
    it('emits every list unconditionally', () => {
      const dto = mapper.toDto(new Workflow({ id: 'p1', name: 'P1' }));

      expect(dto.roles).toEqual([]);
      expect(dto.artifacts).toEqual([]);
      expect(dto.tools).toEqual([]);
      expect(dto.tasks).toEqual([]);
    });

    it('spells an unticked checkbox as false rather than omitting it', () => {
      const dto = mapper.toDto(new Workflow({ tasks: [new WorkflowTaskAssignment({ taskDefinitionId: 't1' })] }));

      expect(dto.tasks?.[0].parallel).toBe(false);
      expect(dto.tasks?.[0].override).toBe(false);
    });

    // Server-computed and marked read-only in the contract: the backend recounts it per list row, so
    // sending it back would be sending a derived value it is about to overwrite.
    it('never sends activeInstances back', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

      expect('activeInstances' in dto).toBe(false);
    });

    // Listed field by field rather than spread, so a control the form may gain later cannot leak.
    it('emits exactly the contract’s fields and nothing else', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_DTO));

      expect(Object.keys(dto).sort()).toEqual(['artifacts', 'createdAt', 'description', 'extends', 'id', 'name', 'roles', 'tasks', 'tools', 'updatedAt', 'version']);
    });
  });
});
