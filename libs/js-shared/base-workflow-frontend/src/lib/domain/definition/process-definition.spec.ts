import { describe, expect, it } from 'vitest';
import { ProcessDefinition, ProcessTaskAssignment } from './process-definition';

describe('ProcessDefinition', () => {
  // All four, and for one reason: a `RELATED_ENTITIES` control needs a list to add its first pick to
  // just as much as an `EMBEDDED_COMPONENTS` one needs a list to append a row to.
  it('defaults every list, so a blank form has something to add to', () => {
    const process = new ProcessDefinition();

    expect(process.roles).toEqual([]);
    expect(process.artifacts).toEqual([]);
    expect(process.tools).toEqual([]);
    expect(process.tasks).toEqual([]);
  });

  it('keeps the reserved word `extends` as the contract spells it', () => {
    const process = new ProcessDefinition({ extends: 'base-workflow' });

    expect(process.extends).toBe('base-workflow');
  });

  it('leaves the server-assigned fields undefined until a backend fills them', () => {
    const process = new ProcessDefinition({ id: 'p1' });

    expect(process.activeInstances).toBeUndefined();
    expect(process.version).toBeUndefined();
    expect(process.createdAt).toBeUndefined();
    expect(process.updatedAt).toBeUndefined();
  });

  // The catalog entities are referenced, not carried: what the process holds is ids, so nothing here
  // can drift from the record under `/roles` or `/artifacts`.
  it('holds its catalog references as plain ids', () => {
    const process = new ProcessDefinition({ roles: ['clerk'], artifacts: ['order-entity'], tools: ['automated-check-tool'] });

    expect(process.roles).toEqual(['clerk']);
    expect(process.artifacts).toEqual(['order-entity']);
    expect(process.tools).toEqual(['automated-check-tool']);
  });
});

describe('ProcessTaskAssignment', () => {
  it('defaults both flags, so an unticked checkbox is false rather than absent', () => {
    const assignment = new ProcessTaskAssignment();

    expect(assignment.parallel).toBe(false);
    expect(assignment.override).toBe(false);
  });

  // A false flag has to survive the round trip as `false`, not as `undefined`: the PUT is a full
  // replacement, so an absent flag is an unset one and an unticked checkbox has to say so.
  it('keeps an explicitly false flag false', () => {
    expect(new ProcessTaskAssignment({ parallel: false, override: false }).parallel).toBe(false);
  });

  it('leaves dependsOn undefined rather than empty', () => {
    expect(new ProcessTaskAssignment().dependsOn).toBeUndefined();
  });

  // The contract gives an assignment no `id`; `taskDefinitionId` is what identifies it within the
  // process. The declared-but-unassigned `id` exists only to satisfy TypeScript's weak-type rule
  // against `BaseEntity`, and `declare` emits nothing — so the payload must not gain an `id` key.
  it('carries no id key at all, the schema giving it none', () => {
    const assignment = new ProcessTaskAssignment({ taskDefinitionId: 'review-order', performedBy: 'clerk' });

    expect(Object.keys(assignment)).toEqual(['taskDefinitionId', 'performedBy', 'dependsOn', 'parallel', 'override']);
    expect('id' in assignment).toBe(false);
  });

  it('mints a blank row an Add can open a form on', () => {
    expect(new ProcessTaskAssignment().taskDefinitionId).toBe('');
    expect(new ProcessTaskAssignment().performedBy).toBe('');
  });
});
