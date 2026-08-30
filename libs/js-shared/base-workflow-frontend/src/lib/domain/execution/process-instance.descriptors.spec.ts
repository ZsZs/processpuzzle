import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createProcessInstanceDescriptor } from './process-instance.descriptors';
import { PROCESS_INSTANCE_ENTITY_NAME, TASK_INSTANCE_ENTITY_NAME, ARTIFACT_INSTANCE_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createProcessInstanceDescriptor', () => {
  const descriptor = createProcessInstanceDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is a routable aggregate of its own', () => {
    expect(descriptor.entityName).toBe(PROCESS_INSTANCE_ENTITY_NAME);
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
    expect(descriptor.scopeRoot()).toBe('base_workflow.process_instance');
  });

  it('describes the run, its references, its timestamps and its two nested lists', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['processDefinitionName', 'status', 'entityId', 'id', 'processDefinitionId', 'startedAt', 'completedAt', 'context', 'tasks', 'artifacts']);
  });

  // The UUID opens the details, but the definition's name is what a monitor recognises a run by — so the
  // status bar has to be told, or it would take the `isLinkToDetails` attribute and show the UUID.
  it('opens the details by id but names the run by its definition', () => {
    expect(byName('id')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('id');
    expect(byName('processDefinitionName')?.isHeading).toBe(true);
    expect(descriptor.titleAttrName()).toBe('processDefinitionName');
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
    expect(descriptor.embeddedAttrDescriptors().map((attr) => attr.referenceIdField)).toEqual(['id', 'id']);
  });
});
