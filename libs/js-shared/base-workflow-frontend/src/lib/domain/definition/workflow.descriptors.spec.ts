import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createWorkflowDescriptor } from './workflow.descriptors';
import {
  WORKFLOW_ARTIFACT_USE_ENTITY_NAME,
  WORKFLOW_ENTITY_NAME,
  WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME,
  WORKFLOW_ROLE_USE_ENTITY_NAME,
  WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME,
  WORKFLOW_TOOL_USE_ENTITY_NAME,
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

  it('describes the header, the revision, the start condition and the five embedded lists', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual([
      'id',
      'name',
      'extends',
      'activeInstances',
      'version',
      'updatedAt',
      'description',
      'startType',
      'eventType',
      'milestoneRef',
      'preconditionExpression',
      'authorizedRoles',
      'payloadMapping',
      'requiredArtifacts',
      'roles',
      'artifacts',
      'tools',
      'tasks',
    ]);
  });

  // Flattened onto this form rather than nested, following the `auth` fields of `Tool Definition`. It has
  // to be on the form at all because the PUT is a full replacement: the entity carried no start
  // condition until this revision, so saving a seeded workflow deleted it.
  it('authors the start condition as flattened fields plus its one list', () => {
    expect(byName('startType')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('startType')?.getSelectables()?.map((selectable) => selectable.key)).toEqual([
      'INPUT_ARTIFACT',
      'TRIGGERING_EVENT',
      'ROLE_DEFINITION',
      'TIME_BASED_PRECONDITION',
    ]);
    expect(byName('authorizedRoles')?.formControlType).toBe(FormControlType.RELATED_ENTITIES);
    expect(byName('authorizedRoles')?.linkedEntityType).toBe(WORKFLOW_ROLE_DEFINITION_ENTITY_NAME);
    expect(byName('payloadMapping')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
    expect(byName('requiredArtifacts')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('requiredArtifacts')?.referenceIdField).toBe('artifactDefinitionId');
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
  // Not `RELATED_ENTITIES` over the catalog aggregate, which is what these were until this revision:
  // `Workflow.roles` is an array of `RoleUse` objects wrapping a `roleDefinitionId`, so the rows are
  // embedded and the `FOREIGN_KEY` *inside* a row is what references the definition. Modelled as ids,
  // every role, artifact and tool loaded empty and saved the wrong shape.
  it('carries the three catalog references as embedded Use rows', () => {
    ['roles', 'artifacts', 'tools'].forEach((attrName) => expect(byName(attrName)?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS));
    expect(byName('roles')?.linkedEntityType).toBe(WORKFLOW_ROLE_USE_ENTITY_NAME);
    expect(byName('artifacts')?.linkedEntityType).toBe(WORKFLOW_ARTIFACT_USE_ENTITY_NAME);
    expect(byName('tools')?.linkedEntityType).toBe(WORKFLOW_TOOL_USE_ENTITY_NAME);
  });

  // A `*Use` has no `id`, so the definition id it wraps is what addresses the row in the URL. Leaving
  // this at the default would make the control look rows up by a field they do not have.
  it('addresses each Use row by the definition id it wraps', () => {
    expect(byName('roles')?.referenceIdField).toBe('roleDefinitionId');
    expect(byName('artifacts')?.referenceIdField).toBe('artifactDefinitionId');
    expect(byName('tools')?.referenceIdField).toBe('toolDefinitionId');
  });

  it('carries five embedded lists, the assignments among them', () => {
    const embedded = descriptor.embeddedAttrDescriptors();

    expect(embedded.map((attr) => attr.attrName)).toEqual(['requiredArtifacts', 'roles', 'artifacts', 'tools', 'tasks']);
    embedded.forEach((attr) => expect(attr.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS));
    expect(descriptor.embeddedAttrFor(WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME)?.attrName).toBe('tasks');
    expect(descriptor.embeddedAttrFor(WORKFLOW_ROLE_USE_ENTITY_NAME)?.attrName).toBe('roles');
    expect(descriptor.embeddedAttrFor(WORKFLOW_ARTIFACT_USE_ENTITY_NAME)?.attrName).toBe('artifacts');
    expect(descriptor.embeddedAttrFor(WORKFLOW_TOOL_USE_ENTITY_NAME)?.attrName).toBe('tools');
    expect(descriptor.embeddedAttrFor(WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME)?.attrName).toBe('requiredArtifacts');
  });

  // An assignment has no `id` of its own; the task it assigns is what identifies it within the workflow.
  it('addresses an assignment by the task it assigns', () => {
    expect(byName('tasks')?.referenceIdField).toBe('taskDefinitionId');
  });

  it('keeps the long, the referencing, the nested and the start-condition fields out of the table', () => {
    expect(byName('description')?.hideInTable).toBe(true);
    ['roles', 'artifacts', 'tools', 'tasks'].forEach((attrName) => expect(byName(attrName)?.hideInTable).toBe(true));
    ['startType', 'eventType', 'milestoneRef', 'preconditionExpression', 'authorizedRoles', 'payloadMapping', 'requiredArtifacts'].forEach((attrName) =>
      expect(byName(attrName)?.hideInTable).toBe(true),
    );
  });
});
