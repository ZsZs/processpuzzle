import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createTaskInstanceDescriptor, TASK_INSTANCE_ID_FIELD } from './task-instance.descriptors';
import { WORKFLOW_INSTANCE_ENTITY_NAME, TASK_INSTANCE_ENTITY_NAME, TASK_STEP_RESULT_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createTaskInstanceDescriptor', () => {
  const descriptor = createTaskInstanceDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is an embedded, read-only component of the workflow instance', () => {
    expect(descriptor.entityName).toBe(TASK_INSTANCE_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([WORKFLOW_INSTANCE_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
    expect(descriptor.isAbstract).toBe(true);
    expect(attrs.every((attr) => attr.disabled)).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.task_instance');
  });

  it('describes where the task stands, who holds it and what its steps produced', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual([
      'name',
      'status',
      'assignedTo',
      'id',
      'taskDefinitionId',
      'activatedAt',
      'completedAt',
      'skippedAt',
      'blockedReason',
      'stepResults',
    ]);
  });

  // An instance id is a UUID; the name — copied from the definition when the instance was created — is
  // what a monitor recognises a row by.
  it('opens the details by name rather than by UUID', () => {
    expect(byName('name')?.isLinkToDetails).toBe(true);
    expect(byName('name')?.isHeading).toBe(true);
    expect(descriptor.componentIdentification()).toBe('name');
  });

  // The parent addresses a row by `id` even though the *link* column is the name: the two are different
  // jobs, and `TASK_INSTANCE_ID_FIELD` is what the parent's `referenceIdField` names.
  it('is addressed by its id from the owning instance', () => {
    expect(TASK_INSTANCE_ID_FIELD).toBe('id');
    expect(byName('id')?.hideInTable).toBe(true);
  });

  it('offers the closed task-status list as a dropdown', () => {
    expect(byName('status')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('status')?.getSelectables()?.map((selectable) => selectable.value)).toEqual(['PENDING', 'ACTIVE', 'COMPLETED', 'SKIPPED', 'BLOCKED']);
  });

  // The one field that explains a stuck workflow, so it earns a column.
  it('shows the blocked reason in the table', () => {
    expect(byName('blockedReason')?.formControlType).toBe(FormControlType.TEXTAREA);
    expect(byName('blockedReason')?.hideInTable).toBe(false);
  });

  it('carries the step results as embedded components addressed by their step id', () => {
    expect(descriptor.embeddedAttrFor(TASK_STEP_RESULT_ENTITY_NAME)?.attrName).toBe('stepResults');
    expect(byName('stepResults')?.referenceIdField).toBe('stepId');
  });
});
