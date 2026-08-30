import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createTaskDefinitionDescriptor } from './task-definition.descriptors';
import { ARTIFACT_DEFINITION_ENTITY_NAME, TASK_DEFINITION_ENTITY_NAME, TASK_STEP_DEFINITION_ENTITY_NAME, WORKFLOW_ROLE_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createTaskDefinitionDescriptor', () => {
  const descriptor = createTaskDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is a standalone aggregate, not a component of a workflow', () => {
    expect(descriptor.entityName).toBe(TASK_DEFINITION_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([]);
    expect(descriptor.isEmbedded).toBeFalsy();
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.task_definition');
    expect(byName('performedByRoles')?.i18nKey()).toBe('base_workflow.task_definition.performedByRoles');
  });

  it('describes the task, its guards, its three reference lists and its steps', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual([
      'id',
      'name',
      'preconditionRuleId',
      'postconditionRuleId',
      'version',
      'updatedAt',
      'description',
      'performedByRoles',
      'inputs',
      'outputs',
      'steps',
    ]);
  });

  // The three fields the reference model moved to `WorkflowTaskAssignment`. A shared task carrying
  // `dependsOn` would be naming siblings of a workflow it knows nothing about.
  it('describes no per-workflow wiring', () => {
    expect(byName('performedBy')).toBeUndefined();
    expect(byName('dependsOn')).toBeUndefined();
    expect(byName('parallel')).toBeUndefined();
    expect(byName('override')).toBeUndefined();
  });

  it('identifies a task by its own id, which every assignment names', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.isHeading).toBe(true);
    expect(byName('id')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('id');
  });

  // Association, not containment: a role exists independently of this task and outlives its reference,
  // so removing a row detaches it rather than deleting the role. The list says who is *able* to perform
  // the task; a workflow pins exactly one of them on its own assignment row.
  it('references the roles able to perform it as an association', () => {
    expect(byName('performedByRoles')?.formControlType).toBe(FormControlType.RELATED_ENTITIES);
    expect(byName('performedByRoles')?.linkedEntityType).toBe(WORKFLOW_ROLE_DEFINITION_ENTITY_NAME);
    expect(byName('performedByRoles')?.required).toBe(true);
    // Left at the default, the role being keyed by `id` like every catalog aggregate.
    expect(byName('performedByRoles')?.referenceIdField).toBe('id');
  });

  // Both name rules owned by base-rule, referenced by id only: this library holds no rule store, and the
  // contract is explicit that base-workflow never duplicates another feature's model.
  it('leaves the rule references as plain text', () => {
    expect(byName('preconditionRuleId')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('postconditionRuleId')?.formControlType).toBe(FormControlType.TEXT_BOX);
  });

  it('shows the server-assigned fields without letting them be edited', () => {
    expect(byName('version')?.disabled).toBe(true);
    expect(byName('updatedAt')?.disabled).toBe(true);
  });

  // Artifact ids, not typed references: an artifact's own `artifactType` already says whether it is an
  // entity, a document or a widget, so there is nothing left for a per-reference `type` to add. Both
  // were `EMBEDDED_COMPONENTS` over a `{ type, refId, label }` row until this revision, against a
  // contract that has had them as id arrays since the catalog split - so every input and output was
  // dropped on load and rejected on save.
  it('references what it reads and writes as artifact associations', () => {
    ['inputs', 'outputs'].forEach((attrName) => {
      expect(byName(attrName)?.formControlType).toBe(FormControlType.RELATED_ENTITIES);
      expect(byName(attrName)?.linkedEntityType).toBe(ARTIFACT_DEFINITION_ENTITY_NAME);
      // Left at the default, the artifact being keyed by `id` like every catalog aggregate.
      expect(byName(attrName)?.referenceIdField).toBe('id');
    });
  });

  // The steps are the one thing still contained: no endpoint of their own, so they travel in the task's
  // payload and are addressed through it.
  it('contains only its steps, addressed by their own id', () => {
    expect(descriptor.embeddedAttrDescriptors().map((attr) => attr.attrName)).toEqual(['steps']);
    expect(descriptor.embeddedAttrFor(TASK_STEP_DEFINITION_ENTITY_NAME)?.attrName).toBe('steps');
    expect(byName('steps')?.referenceIdField).toBe('id');
  });
});
