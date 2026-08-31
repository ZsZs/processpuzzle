import { describe, expect, it } from 'vitest';
import { ArtifactUse, JoinType, RequiredStartArtifact, RoleUse, ToolUse, Workflow, WorkflowTaskAssignment } from './workflow';

describe('Workflow', () => {
  // All four, and for one reason: a `RELATED_ENTITIES` control needs a list to add its first pick to
  // just as much as an `EMBEDDED_COMPONENTS` one needs a list to append a row to.
  it('defaults every list, so a blank form has something to add to', () => {
    const workflow = new Workflow();

    expect(workflow.roles).toEqual([]);
    expect(workflow.artifacts).toEqual([]);
    expect(workflow.tools).toEqual([]);
    expect(workflow.tasks).toEqual([]);
  });

  it('keeps the reserved word `extends` as the contract spells it', () => {
    const workflow = new Workflow({ extends: 'base-workflow' });

    expect(workflow.extends).toBe('base-workflow');
  });

  it('leaves the server-assigned fields undefined until a backend fills them', () => {
    const workflow = new Workflow({ id: 'p1' });

    expect(workflow.activeInstances).toBeUndefined();
    expect(workflow.version).toBeUndefined();
    expect(workflow.createdAt).toBeUndefined();
    expect(workflow.updatedAt).toBeUndefined();
  });

  // The catalog entities are referenced, not carried, but what the workflow holds is not a bare id
  // either: it is a `*Use` row wrapping one, which is the contract's shape and the extension point for
  // per-workflow configuration of a shared definition.
  it('holds its catalog references as Use rows wrapping a definition id', () => {
    const workflow = new Workflow({
      roles: [new RoleUse({ roleDefinitionId: 'clerk' })],
      artifacts: [new ArtifactUse({ artifactDefinitionId: 'order-entity' })],
      tools: [new ToolUse({ toolDefinitionId: 'automated-check-tool' })],
    });

    expect(workflow.roles).toEqual([{ roleDefinitionId: 'clerk' }]);
    expect(workflow.artifacts).toEqual([{ artifactDefinitionId: 'order-entity' }]);
    expect(workflow.tools).toEqual([{ toolDefinitionId: 'automated-check-tool' }]);
  });

  // Flattened onto the entity rather than nested, following the `auth` fields of `Tool Definition`: the
  // generic form builds one control per attribute, so a nested object needs an embedded entity and a
  // start condition is not a list. It has to be modelled at all because the PUT is a full replacement -
  // a field the entity does not carry is a field the next save deletes, and this one was absent.
  it('carries the start condition flattened, its one list defaulted', () => {
    const workflow = new Workflow({ id: 'p1' });

    expect(workflow.startType).toBeUndefined();
    expect(workflow.requiredArtifacts).toEqual([]);
    expect(workflow.authorizedRoles).toEqual([]);
    expect(workflow.eventType).toBeUndefined();
    expect(workflow.payloadMapping).toBeUndefined();
    expect(workflow.milestoneRef).toBeUndefined();
    expect(workflow.preconditionExpression).toBeUndefined();
  });
});

describe('the Use rows', () => {
  // Each wraps one id and the contract gives it no key of its own, so the declared-but-unassigned `id`
  // must not reach the payload: `declare` emits nothing, which is what keeps the row exactly the shape
  // the schema describes.
  it('carry no id key at all, the schema giving them none', () => {
    [new RoleUse(), new ArtifactUse(), new ToolUse(), new RequiredStartArtifact()].forEach((use) => expect('id' in use).toBe(false));
  });

  it('mint a blank row an Add can open a form on', () => {
    expect(new RoleUse().roleDefinitionId).toBe('');
    expect(new ArtifactUse().artifactDefinitionId).toBe('');
    expect(new ToolUse().toolDefinitionId).toBe('');
  });

  // Absent rather than empty: no state at all means any state will do, which is a different statement
  // from requiring a state named by the empty string.
  it('leave a required artifact state undefined rather than empty', () => {
    expect(new RequiredStartArtifact({ artifactDefinitionId: 'order-entity' }).state).toBeUndefined();
  });
});

describe('WorkflowTaskAssignment', () => {
  it('defaults both flags, so an unticked checkbox is false rather than absent', () => {
    const assignment = new WorkflowTaskAssignment();

    expect(assignment.parallel).toBe(false);
    expect(assignment.override).toBe(false);
  });

  // A false flag has to survive the round trip as `false`, not as `undefined`: the PUT is a full
  // replacement, so an absent flag is an unset one and an unticked checkbox has to say so.
  it('keeps an explicitly false flag false', () => {
    expect(new WorkflowTaskAssignment({ parallel: false, override: false }).parallel).toBe(false);
  });

  it('leaves dependsOn undefined rather than empty', () => {
    expect(new WorkflowTaskAssignment().dependsOn).toBeUndefined();
  });

  // Immaterial when `dependsOn` is empty, so absent rather than defaulted to the contract's `ALL`: the
  // server applies the default, and sending it would claim the author chose it.
  it('leaves joinType undefined rather than defaulting it', () => {
    expect(new WorkflowTaskAssignment().joinType).toBeUndefined();
    expect(new WorkflowTaskAssignment({ joinType: JoinType.ANY }).joinType).toBe(JoinType.ANY);
  });

  // The contract gives an assignment no `id`; `taskDefinitionId` is what identifies it within the
  // workflow. The declared-but-unassigned `id` exists only to satisfy TypeScript's weak-type rule
  // against `BaseEntity`, and `declare` emits nothing — so the payload must not gain an `id` key.
  it('carries no id key at all, the schema giving it none', () => {
    const assignment = new WorkflowTaskAssignment({ taskDefinitionId: 'review-order', performedBy: 'clerk' });

    expect(Object.keys(assignment)).toEqual(['taskDefinitionId', 'performedBy', 'dependsOn', 'joinType', 'parallel', 'override']);
    expect('id' in assignment).toBe(false);
  });

  it('mints a blank row an Add can open a form on', () => {
    expect(new WorkflowTaskAssignment().taskDefinitionId).toBe('');
    expect(new WorkflowTaskAssignment().performedBy).toBe('');
  });
});
