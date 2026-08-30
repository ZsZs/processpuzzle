import { describe, expect, it } from 'vitest';
import { WorkflowInstance, WorkflowInstanceStatus, TaskInstance, TaskInstanceStatus, ArtifactInstance } from './workflow-instance';
import { WorkflowInstanceMapper } from './workflow-instance.mapper';
import { OTHER_WORKFLOW_INSTANCE_DTO, WORKFLOW_INSTANCE_DTO } from './test-workflow-instance';
import { ArtifactType } from '../definition/artifact-definition';

describe('WorkflowInstanceMapper', () => {
  const mapper = new WorkflowInstanceMapper();

  describe('fromDto', () => {
    const instance = mapper.fromDto(WORKFLOW_INSTANCE_DTO);

    it('reads the header of a running instance', () => {
      expect(instance.id).toBe('8f14e45f-ceea-467a-9c9b-9b0c1f0f5a01');
      expect(instance.workflowId).toBe('order-fulfillment-workflow');
      expect(instance.workflowName).toBe('Order Fulfillment Workflow');
      expect(instance.status).toBe(WorkflowInstanceStatus.ACTIVE);
      expect(instance.entityId).toBe('1');
      expect(instance.context).toEqual({ channel: 'web', priority: 'normal' });
    });

    it('maps every task and artifact rather than passing the lists through', () => {
      expect(instance.tasks).toHaveLength(3);
      expect(instance.tasks[0]).toBeInstanceOf(TaskInstance);
      expect(instance.tasks.map((task) => task.status)).toEqual([TaskInstanceStatus.COMPLETED, TaskInstanceStatus.ACTIVE, TaskInstanceStatus.PENDING]);
      expect(instance.artifacts[0]).toBeInstanceOf(ArtifactInstance);
      expect(instance.artifacts[0].type).toBe(ArtifactType.ENTITY);
      expect(instance.artifacts[0].currentState).toBe('CONFIRMED');
    });

    it('maps the third level down — a task’s step results', () => {
      expect(instance.tasks[0].stepResults).toHaveLength(1);
      expect(instance.tasks[0].stepResults[0].stepId).toBe('check-items');
      expect(instance.tasks[0].stepResults[0].toolResponse).toEqual({ available: 'true', warehouse: 'EU-1' });
    });

    it('reads the failure path — a blocked task and a step that errored', () => {
      const [blocked] = mapper.fromDto(OTHER_WORKFLOW_INSTANCE_DTO).tasks;

      expect(blocked.status).toBe(TaskInstanceStatus.BLOCKED);
      expect(blocked.blockedReason).toBe('positive-quantities: line item 3 has quantity 0');
      expect(blocked.stepResults[0].error).toBe('tool returned 503 after 3 retries');
    });

    it('defaults a list the document omits', () => {
      const bare = mapper.fromDto({ id: 'i1' });

      expect(bare.tasks).toEqual([]);
      expect(bare.artifacts).toEqual([]);
    });

    it('defaults the step results of a task that declares none', () => {
      expect(mapper.fromDto({ tasks: [{ id: 't1' }] }).tasks[0].stepResults).toEqual([]);
    });
  });

  // `toDto` is required by `BaseEntityMapper` but never reaches the network: the contract defines no
  // PUT here. It is implemented faithfully rather than left throwing, so the store's optimistic paths
  // and a future action surface have a payload to build on.
  describe('toDto', () => {
    it('round-trips the instance without losing a nested row', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_INSTANCE_DTO));

      expect(dto.tasks).toHaveLength(3);
      expect(dto.tasks?.[0].stepResults).toHaveLength(1);
      expect(dto.artifacts).toHaveLength(2);
      expect(dto.artifacts?.[0].stateMachineInstanceId).toBe('order-1');
    });

    it('emits both nested lists unconditionally', () => {
      const dto = mapper.toDto(new WorkflowInstance({ id: 'i1' }));

      expect(dto.tasks).toEqual([]);
      expect(dto.artifacts).toEqual([]);
    });

    it('emits exactly the contract’s fields and nothing else', () => {
      const dto = mapper.toDto(mapper.fromDto(WORKFLOW_INSTANCE_DTO));

      expect(Object.keys(dto).sort()).toEqual(['artifacts', 'completedAt', 'context', 'entityId', 'id', 'startedAt', 'status', 'tasks', 'workflowId', 'workflowName']);
    });
  });
});
