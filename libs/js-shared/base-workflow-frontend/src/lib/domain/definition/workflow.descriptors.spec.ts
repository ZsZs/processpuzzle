import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createWorkflowDescriptor } from './workflow.descriptors';
import {
  ARTIFACT_DEFINITION_ENTITY_NAME,
  WORKFLOW_ENTITY_NAME,
  WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME,
  TOOL_DEFINITION_ENTITY_NAME,
  WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
} from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createWorkflowDescriptor', () => {
  const descriptor = createWorkflowDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(WORKFLOW_ENTITY_NAME);
  });

  it('is a routable aggregate, not a component of anything', () => {
    expect(descriptor.componentParents).toEqual([]);
    expect(descriptor.isEmbedded).toBe(false);
    expect(descriptor.isAbstract).toBe(false);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.workflow');
    expect(descriptor.i18nKey()).toBe('base_workflow.workflow._self');
    expect(byName('tasks')?.i18nKey()).toBe('base_workflow.workflow.tasks');
  });

  it('describes the header, the revision, the three references and the one embedded list', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'name', 'extends', 'activeInstances', 'version', 'updatedAt', 'description', 'roles', 'artifacts', 'tools', 'tasks']);
  });

  // The author-chosen id *is* the record's identity here, unlike base-state's machine where `id` mirrors
  // `entityName` — so nothing has to be mirrored and the list's own column opens the workflow.
  it('identifies a workflow by its author-chosen id', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.isLinkToDetails).toBe(true);
    expect(byName('id')?.isHeading).toBe(true);
    expect(descriptor.componentIdentification()).toBe('id');
    expect(descriptor.titleAttrName()).toBe('id');
  });

  it('offers `extends` as a reference to another workflow, so a cycle is at least visible', () => {
    expect(byName('extends')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('extends')?.linkedEntityType).toBe(WORKFLOW_ENTITY_NAME);
  });

  it('shows the server-assigned fields without letting them be edited', () => {
    expect(byName('activeInstances')?.disabled).toBe(true);
    expect(byName('version')?.disabled).toBe(true);
    expect(byName('updatedAt')?.disabled).toBe(true);
  });

  // Association, not containment, and this is the change the reference model is: each of the three
  // points at a catalog aggregate with a list screen of its own, so adding a row picks an existing
  // record and removing one only detaches the reference — it never deletes the role or the artifact.
  it('references the three catalog aggregates rather than containing them', () => {
    ['roles', 'artifacts', 'tools'].forEach((attrName) => expect(byName(attrName)?.formControlType).toBe(FormControlType.RELATED_ENTITIES));
    expect(byName('roles')?.linkedEntityType).toBe(WORKFLOW_ROLE_DEFINITION_ENTITY_NAME);
    expect(byName('artifacts')?.linkedEntityType).toBe(ARTIFACT_DEFINITION_ENTITY_NAME);
    expect(byName('tools')?.linkedEntityType).toBe(TOOL_DEFINITION_ENTITY_NAME);
  });

  // All three catalog aggregates are keyed by `id`, so the default is right and naming anything else
  // would make the control look rows up by a field they do not have.
  it('leaves the reference id field at its default for all three', () => {
    ['roles', 'artifacts', 'tools'].forEach((attrName) => expect(byName(attrName)?.referenceIdField).toBe('id'));
  });

  // The one thing a workflow still owns: an assignment pairs a shared task with the role performing it
  // *here*, so it has no meaning outside this workflow and travels inside its payload.
  it('carries only the task assignments as embedded components of itself', () => {
    const embedded = descriptor.embeddedAttrDescriptors();

    expect(embedded.map((attr) => attr.attrName)).toEqual(['tasks']);
    expect(embedded[0].formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(descriptor.embeddedAttrFor(WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME)?.attrName).toBe('tasks');
  });

  // An assignment has no `id` of its own; the task it assigns is what identifies it within the workflow.
  it('addresses an assignment by the task it assigns', () => {
    expect(byName('tasks')?.referenceIdField).toBe('taskDefinitionId');
  });

  it('keeps the long, the referencing and the nested fields out of the table', () => {
    expect(byName('description')?.hideInTable).toBe(true);
    ['roles', 'artifacts', 'tools', 'tasks'].forEach((attrName) => expect(byName(attrName)?.hideInTable).toBe(true));
  });
});
