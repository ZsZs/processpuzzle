import { describe, expect, it } from 'vitest';
import { ProcessInstance, ProcessInstanceStatus, StepResult, TaskInstance, TaskInstanceStatus, ArtifactInstance } from './process-instance';

describe('ProcessInstance', () => {
  it('defaults both embedded lists', () => {
    const instance = new ProcessInstance();

    expect(instance.tasks).toEqual([]);
    expect(instance.artifacts).toEqual([]);
  });

  it('mirrors the contract status enums', () => {
    expect(Object.keys(ProcessInstanceStatus)).toEqual(['ACTIVE', 'COMPLETED', 'CANCELLED', 'SUSPENDED']);
    expect(Object.keys(TaskInstanceStatus)).toEqual(['PENDING', 'ACTIVE', 'COMPLETED', 'SKIPPED', 'BLOCKED']);
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
