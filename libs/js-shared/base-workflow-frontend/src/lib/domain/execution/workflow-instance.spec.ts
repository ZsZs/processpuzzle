import { describe, expect, it } from 'vitest';
import { WorkflowInstance, WorkflowInstanceStatus, StepResult, TaskInstance, TaskInstanceStatus, ArtifactInstance } from './workflow-instance';

describe('WorkflowInstance', () => {
  it('defaults both embedded lists', () => {
    const instance = new WorkflowInstance();

    expect(instance.tasks).toEqual([]);
    expect(instance.artifacts).toEqual([]);
  });

  // Every run of a workflow shares its name, so the number is what tells two runs apart in a title.
  it('titles the run by its workflow and number, and by whichever of the two it has', () => {
    expect(new WorkflowInstance({ workflowName: 'Order Fulfillment', instanceNumber: 3 }).title).toBe('Order Fulfillment #3');
    expect(new WorkflowInstance({ workflowName: 'Order Fulfillment' }).title).toBe('Order Fulfillment');
    expect(new WorkflowInstance({ instanceNumber: 3 }).title).toBe('#3');
  });

  it('mirrors the contract status enums', () => {
    expect(Object.keys(WorkflowInstanceStatus)).toEqual(['ACTIVE', 'COMPLETED', 'CANCELLED', 'SUSPENDED']);
    expect(Object.keys(TaskInstanceStatus)).toEqual(['PENDING', 'ACTIVE', 'COMPLETED', 'SKIPPED', 'BLOCKED', 'CANCELLED']);
  });
});

describe('TaskInstance', () => {
  it('defaults its step results', () => {
    expect(new TaskInstance().stepResults).toEqual([]);
  });

  it('keeps the blocked reason, which is the only field that explains a stuck run', () => {
    const task = new TaskInstance({ status: TaskInstanceStatus.BLOCKED, blockedReason: 'quantity must be positive' });

    expect(task.blockedReason).toBe('quantity must be positive');
  });
});

describe('StepResult', () => {
  // The contract gives a step result no `id`; `stepId` is what identifies it. `declare` emits nothing,
  // so the payload must not gain an `id` key.
  it('carries no id key at all, the schema giving it none', () => {
    const result = new StepResult({ stepId: 'check-items' });

    expect(Object.keys(result)).toEqual(['stepId', 'completedAt', 'toolResponse', 'error']);
    expect('id' in result).toBe(false);
  });
});

describe('ArtifactInstance', () => {
  it('mints a blank row and keeps the cross-feature references it is mostly made of', () => {
    const artifact = new ArtifactInstance({ entityId: '1', stateMachineInstanceId: 'order-1', currentState: 'CONFIRMED' });

    expect(artifact.id).toBe('');
    expect(artifact.entityId).toBe('1');
    expect(artifact.stateMachineInstanceId).toBe('order-1');
    expect(artifact.currentState).toBe('CONFIRMED');
  });
});
