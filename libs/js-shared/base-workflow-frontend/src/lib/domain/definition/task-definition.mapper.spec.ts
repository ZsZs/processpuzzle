import { describe, expect, it } from 'vitest';
import { TaskDefinition, TaskStepType } from './task-definition';
import { TaskDefinitionMapper } from './task-definition.mapper';
import { OTHER_TASK_DEFINITION_DTO, TASK_DEFINITION_DTO } from './test-task-definition';

describe('TaskDefinitionMapper', () => {
  const mapper = new TaskDefinitionMapper();

  describe('fromDto', () => {
    const task = mapper.fromDto(TASK_DEFINITION_DTO);

    it('reads the seeded task', () => {
      expect(task).toBeInstanceOf(TaskDefinition);
      expect(task.id).toBe('review-order');
      expect(task.performedByRoles).toEqual(['clerk', 'manager']);
      expect(task.preconditionRuleId).toBe('positive-quantities');
      expect(task.version).toBe(1);
    });

    // Plain artifact definition ids, which is what the contract has had here since the catalog split.
    it('reads what the task consumes and produces as artifact ids', () => {
      expect(task.inputs).toEqual(['order-entity']);
      expect(task.outputs).toEqual(['order-entity']);
    });

    // Mapped field by field rather than passed through, which is what makes a wire-name mismatch a
    // failure here rather than a silently empty control: the mapper read `toolId` where the contract
    // says `toolDefinitionId`, so every service step lost its tool binding on the next save.
    it('maps a step under the contract own field names', () => {
      expect(task.steps[0].stepType).toBe(TaskStepType.SERVICE_STEP);
      expect(task.steps[0].toolDefinitionId).toBe('automated-check-tool');
      expect(task.steps[0].toolOperation).toBe('inventory-check');
    });

    it('flattens inputs and outputs that arrived as whole artifacts', () => {
      const embedded = mapper.fromDto({ id: 't1', inputs: [{ id: 'order-entity', name: 'Order' }], outputs: ['fulfillment-invoice'] });

      expect(embedded.inputs).toEqual(['order-entity']);
      expect(embedded.outputs).toEqual(['fulfillment-invoice']);
    });

    // Applied on the way *in* as well as out, so a payload holding embedded roles loads as ids rather
    // than half-flattening on the next save.
    it('flattens performedByRoles when it arrived as whole roles', () => {
      const embedded = mapper.fromDto({ id: 't1', performedByRoles: [{ id: 'clerk', name: 'Order Clerk' }] });

      expect(embedded.performedByRoles).toEqual(['clerk']);
    });

    it('defaults every list of a task that declares none', () => {
      const bare = mapper.fromDto(OTHER_TASK_DEFINITION_DTO);

      expect(bare.inputs).toEqual([]);
      expect(bare.outputs).toEqual([]);
      expect(bare.steps).toEqual([]);
    });
  });

  describe('toDto', () => {
    it('round-trips the seeded task without losing a nested row', () => {
      const dto = mapper.toDto(mapper.fromDto(TASK_DEFINITION_DTO));

      expect(dto.performedByRoles).toEqual(['clerk', 'manager']);
      expect(dto.inputs).toEqual(['order-entity']);
      expect(dto.outputs).toEqual(['order-entity']);
      expect(dto.steps?.[0].id).toBe('check-items');
      expect(dto.steps?.[0].stepType).toBe(TaskStepType.SERVICE_STEP);
      expect(dto.steps?.[0].toolDefinitionId).toBe('automated-check-tool');
    });

    // The save path of the `RELATED_ENTITIES` control over the roles: the user picked one from its own
    // list, so the control put the whole record in the form value, and the contract wants the id.
    it('flattens the roles the reference control wrote on selection', () => {
      const task = new TaskDefinition({ id: 't1' });
      (task as unknown as Record<string, unknown>)['performedByRoles'] = ['clerk', { id: 'manager' }];

      expect(mapper.toDto(task).performedByRoles).toEqual(['clerk', 'manager']);
    });

    // `PUT /tasks/{taskId}` is a full replacement, so an absent list is an emptied one.
    it('emits every list unconditionally', () => {
      const dto = mapper.toDto(new TaskDefinition({ id: 't1', name: 'T1' }));

      expect(dto.performedByRoles).toEqual([]);
      expect(dto.inputs).toEqual([]);
      expect(dto.outputs).toEqual([]);
      expect(dto.steps).toEqual([]);
    });

    // Listed field by field rather than spread — and what is *not* in this list is the point: the three
    // per-workflow wiring fields belong to `WorkflowTaskAssignment` now.
    it('emits exactly the contract’s fields and nothing else', () => {
      const dto = mapper.toDto(mapper.fromDto(TASK_DEFINITION_DTO));

      expect(Object.keys(dto).sort()).toEqual([
        'createdAt',
        'description',
        'id',
        'inputs',
        'name',
        'outputs',
        'performedByRoles',
        'postconditionRuleId',
        'preconditionRuleId',
        'steps',
        'updatedAt',
        'version',
      ]);
    });
  });
});
