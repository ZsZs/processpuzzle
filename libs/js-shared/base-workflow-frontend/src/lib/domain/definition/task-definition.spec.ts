import { describe, expect, it } from 'vitest';
import { ReferenceType, StepDefinition, TaskDefinition, TaskIOReference } from './task-definition';

describe('TaskDefinition', () => {
  it('defaults its three embedded lists and the roles able to perform it', () => {
    const task = new TaskDefinition();

    expect(task.performedByRoles).toEqual([]);
    expect(task.inputs).toEqual([]);
    expect(task.outputs).toEqual([]);
    expect(task.steps).toEqual([]);
  });

  // The three fields that moved to `ProcessTaskAssignment`. Asserted as absent rather than left
  // untested: a shared task carrying `dependsOn` would be naming siblings of a process it knows
  // nothing about, which is exactly the bug the reference model exists to remove.
  it('describes no per-process wiring at all', () => {
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

describe('TaskIOReference', () => {
  // The contract gives a reference no `id`; `refId` is what identifies it. The declared-but-unassigned
  // `id` exists only to satisfy TypeScript's weak-type rule against `BaseEntity`, and `declare` emits
  // nothing — so the payload must not gain an `id` key.
  it('carries no id key at all, the schema giving it none', () => {
    const reference = new TaskIOReference({ type: ReferenceType.DOCUMENT, refId: 'invoice' });

    expect(Object.keys(reference)).toEqual(['type', 'refId', 'label']);
    expect('id' in reference).toBe(false);
  });

  // `ARTIFACT` is what the catalog model added, and it comes first because it is the only kind whose
  // lifecycle base-state can govern.
  it('mirrors the contract enum, artifact references included', () => {
    expect(Object.keys(ReferenceType)).toEqual(['ARTIFACT', 'BASE_ENTITY', 'DOCUMENT', 'WIDGET']);
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
