import { describe, expect, it } from 'vitest';
import { StepDefinition, TaskDefinition, TaskStepType } from './task-definition';

describe('TaskDefinition', () => {
  it('defaults its three reference lists and its steps', () => {
    const task = new TaskDefinition();

    expect(task.performedByRoles).toEqual([]);
    expect(task.inputs).toEqual([]);
    expect(task.outputs).toEqual([]);
    expect(task.steps).toEqual([]);
  });

  // The three fields that moved to `WorkflowTaskAssignment`. Asserted as absent rather than left
  // untested: a shared task carrying `dependsOn` would be naming siblings of a workflow it knows
  // nothing about, which is exactly the bug the reference model exists to remove.
  it('describes no per-workflow wiring at all', () => {
    const task = new TaskDefinition({ id: 'review-order' }) as unknown as Record<string, unknown>;

    expect('dependsOn' in task).toBe(false);
    expect('parallel' in task).toBe(false);
    expect('override' in task).toBe(false);
    expect('performedBy' in task).toBe(false);
  });

  it('leaves the server-assigned fields undefined until a backend fills them', () => {
    const task = new TaskDefinition({ id: 'review-order' });

    expect(task.version).toBeUndefined();
    expect(task.createdAt).toBeUndefined();
    expect(task.updatedAt).toBeUndefined();
  });
});

describe('TaskStepType', () => {
  // `SERVICE_STEP` is the one that reads `toolDefinitionId` and `toolOperation`; on a `USER_STEP` the
  // engine ignores both.
  it('mirrors the contract enum', () => {
    expect(Object.keys(TaskStepType)).toEqual(['USER_STEP', 'SERVICE_STEP']);
  });
});

describe('StepDefinition', () => {
  it('mints a blank row an Add can open a form on', () => {
    const step = new StepDefinition();

    expect(step.id).toBe('');
    expect(step.name).toBe('');
  });

  // Open maps, not lists: absent stays absent, so a purely manual step does not claim to have an
  // empty mapping the engine would then try to apply.
  it('leaves the tool mappings undefined rather than empty', () => {
    const step = new StepDefinition({ id: 'manager-signoff' });

    expect(step.inputMapping).toBeUndefined();
    expect(step.outputMapping).toBeUndefined();
  });
});
