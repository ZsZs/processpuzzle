import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createWorkflowTaskAssignmentDescriptor, WORKFLOW_TASK_ASSIGNMENT_ID_FIELD } from './workflow-task-assignment.descriptors';
import { WORKFLOW_ENTITY_NAME, WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME, TASK_DEFINITION_ENTITY_NAME, WORKFLOW_ROLE_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createWorkflowTaskAssignmentDescriptor', () => {
  const descriptor = createWorkflowTaskAssignmentDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  // The only embedded child a workflow has left, and the only place per-workflow wiring lives.
  it('is an embedded component of the workflow definition', () => {
    expect(descriptor.entityName).toBe(WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([WORKFLOW_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
    // An embedded component is located by its position in the parent's payload, not by a foreign key.
    expect(descriptor.parentReferenceAttrName()).toBeUndefined();
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.workflow_task_assignment');
    expect(byName('taskDefinitionId')?.i18nKey()).toBe('base_workflow.workflow_task_assignment.taskDefinitionId');
  });

  it('describes the task, the performing role and the ordering', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['taskDefinitionId', 'performedBy', 'dependsOn', 'parallel', 'override']);
  });

  // The assignment has no id of its own: the task it assigns is what identifies it, and the contract's
  // at-most-once-per-workflow rule is what makes that unique.
  it('is identified by the task it assigns rather than by an id of its own', () => {
    expect(WORKFLOW_TASK_ASSIGNMENT_ID_FIELD).toBe('taskDefinitionId');
    expect(descriptor.componentIdentification()).toBe('taskDefinitionId');
    expect(byName('taskDefinitionId')?.isHeading).toBe(true);
    expect(byName('taskDefinitionId')?.isLinkToDetails).toBe(true);
  });

  // Both are catalog aggregates with stores of their own, so both resolve to a display name and a
  // details link — unlike the pre-catalog model, where the role was a sibling row of one workflow.
  it('resolves both references against their own catalog aggregates', () => {
    expect(byName('taskDefinitionId')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('taskDefinitionId')?.linkedEntityType).toBe(TASK_DEFINITION_ENTITY_NAME);
    expect(byName('taskDefinitionId')?.required).toBe(true);
    expect(byName('performedBy')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('performedBy')?.linkedEntityType).toBe(WORKFLOW_ROLE_DEFINITION_ENTITY_NAME);
    expect(byName('performedBy')?.required).toBe(true);
  });

  // `dependsOn` names sibling rows of the list being edited in the same form, so any closed option list
  // would be stale the moment an assignment is added.
  it('edits dependsOn as chips rather than a picker', () => {
    expect(byName('dependsOn')?.formControlType).toBe(FormControlType.TAGS);
    expect(byName('dependsOn')?.hideInTable).toBe(true);
  });

  it('shows both control-flow flags in the table as checkboxes', () => {
    expect(byName('parallel')?.formControlType).toBe(FormControlType.CHECKBOX);
    expect(byName('override')?.formControlType).toBe(FormControlType.CHECKBOX);
    expect(byName('parallel')?.hideInTable).toBe(false);
    expect(byName('override')?.hideInTable).toBe(false);
  });
});
