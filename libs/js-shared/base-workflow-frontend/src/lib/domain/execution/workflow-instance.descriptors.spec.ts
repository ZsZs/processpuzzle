import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, DEFAULT_DATE_TIME_FORMAT, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createWorkflowInstanceDescriptor } from './workflow-instance.descriptors';
import { WORKFLOW_INSTANCE_ENTITY_NAME, TASK_INSTANCE_ENTITY_NAME, ARTIFACT_INSTANCE_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createWorkflowInstanceDescriptor', () => {
  const descriptor = createWorkflowInstanceDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is a routable aggregate of its own', () => {
    expect(descriptor.entityName).toBe(WORKFLOW_INSTANCE_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([]);
    expect(descriptor.isEmbedded).toBe(false);
  });

  // The contract defines no PUT on the runtime side: an instance is started by POST, cancelled by DELETE
  // and never edited. `isAbstract` is what disables New, Edit and Delete in the toolbar and Save and
  // Delete on the form.
  it('is read-only by contract, on both levers at once', () => {
    expect(descriptor.isAbstract).toBe(true);
    expect(attrs.every((attr) => attr.disabled)).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.workflow_instance');
  });

  it('describes the run, its references, its timestamps and its two nested lists', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['instanceNumber', 'workflowName', 'status', 'entityLabel', 'workflowId', 'startEventId', 'startedAt', 'completedAt', 'context', 'tasks', 'artifacts', 'events']);
  });

  // The list's link opens the run itself, so it is the run's own identity — the server-assigned number —
  // not the workflow's name, which belongs to the related definition and is shared by every run of it.
  it('links by the instance number, titles by workflow and number, and shows no database key', () => {
    expect(byName('instanceNumber')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('instanceNumber');
    expect(byName('workflowName')?.isLinkToDetails).toBeFalsy();
    expect(descriptor.titleAttrName()).toBe('title');
    // The database keys are not shown at all — the UUID is in the URL, and the subject is shown by name.
    expect(byName('id')).toBeUndefined();
    expect(byName('entityId')).toBeUndefined();
  });

  it('shows the subject by its name rather than its id', () => {
    expect(byName('entityLabel')?.hideInTable).toBeFalsy();
    expect(byName('entityLabel')?.disabled).toBe(true);
  });

  it('renders the timestamps as date and time in the default format', () => {
    for (const name of ['startedAt', 'completedAt']) {
      expect(byName(name)?.formControlType).toBe(FormControlType.DATE);
      expect(byName(name)?.dateFormat).toEqual(DEFAULT_DATE_TIME_FORMAT);
      expect(byName(name)?.disabled).toBe(true);
    }
  });

  it('offers the closed instance-status list as a dropdown', () => {
    expect(byName('status')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('status')?.getSelectables()?.map((selectable) => selectable.value)).toEqual(['ACTIVE', 'COMPLETED', 'CANCELLED', 'SUSPENDED']);
  });

  it('shows the context as an open key/value map', () => {
    expect(byName('context')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
    expect(byName('context')?.hideInTable).toBe(true);
  });

  it('carries both nested lists as embedded components addressed by their own id', () => {
    expect(descriptor.embeddedAttrFor(TASK_INSTANCE_ENTITY_NAME)?.attrName).toBe('tasks');
    expect(descriptor.embeddedAttrFor(ARTIFACT_INSTANCE_ENTITY_NAME)?.attrName).toBe('artifacts');
    expect(descriptor.embeddedAttrDescriptors().map((attr) => attr.referenceIdField)).toEqual(['id', 'id', 'id']);
  });
});
