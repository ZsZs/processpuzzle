import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { WORKFLOW_I18N_SCOPE } from '../../base-workflow.i18n';
import {
  WORKFLOW_ARTIFACT_USE_ENTITY_NAME,
  WORKFLOW_ENTITY_NAME,
  WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME,
  WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
  WORKFLOW_ROLE_USE_ENTITY_NAME,
  WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME,
  WORKFLOW_TOOL_USE_ENTITY_NAME,
} from '../workflow-entity-names';
import { WORKFLOW_REQUIRED_START_ARTIFACT_ID_FIELD } from './required-start-artifact.descriptors';
import { WorkflowStartConditionType } from './workflow';
import { WORKFLOW_TASK_ASSIGNMENT_ID_FIELD } from './workflow-task-assignment.descriptors';
import { WORKFLOW_ARTIFACT_USE_ID_FIELD, WORKFLOW_ROLE_USE_ID_FIELD, WORKFLOW_TOOL_USE_ID_FIELD } from './workflow-use.descriptors';

export { WORKFLOW_ENTITY_NAME };

const startConditionTypeSelectables = toSelectables(Object.keys(WorkflowStartConditionType));

function createWorkflowAttrDescriptors(): AbstractAttrDescriptor[] {
  // The business key *and* the record's identity: the contract addresses a workflow by the
  // author-chosen id, which is what the generic screens address a record by too — so unlike
  // base-state's `StateMachineDefinition`, nothing has to be mirrored here. `isLinkToDetails`, so the
  // list's own column opens the workflow, and `isHeading`, so the form and the status bar name it.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique per organization, e.g. order-fulfillment-workflow';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // A workflow this one inherits roles, artifacts, tools and task assignments from — another row of
  // *this* list, so a real reference the framework can resolve and navigate to. Same shape as
  // base-rule's `extendsRuleId`, and the backend's `WorkflowExtendsValidator` refuses a cycle.
  const extendsAttr = new BaseEntityAttrDescriptor('extends', FormControlType.FOREIGN_KEY, 'Extends');
  extendsAttr.linkedEntityType = WORKFLOW_ENTITY_NAME;
  extendsAttr.hideInTable = true;

  // Server-assigned: shown so the author can see where a definition stands, never edited here.
  // `activeInstances` is why the list endpoint returns the full entity rather than a summary — see
  // the contract's note on `listWorkflows`.
  const activeInstancesAttr = new BaseEntityAttrDescriptor('activeInstances', FormControlType.TEXT_BOX, 'Active Instances');
  activeInstancesAttr.disabled = true;

  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  // region start condition — the contract's nested `startCondition`, flattened
  // Flattened onto this form rather than modelled as a nested entity, following the `auth` fields of
  // `Tool Definition`: the generic form builds one control per attribute of one entity, and a start
  // condition is a single object rather than a list. `WorkflowMapper` re-nests all seven on save.
  //
  // Every field is shown regardless of `startType`, because base-entity has no conditional-visibility
  // mechanism and the backend ignores rather than rejects the fields the chosen type does not read.
  // The placeholders name the type each belongs to, which is the honest substitute.
  //
  // None of them appears in the table: what starts a workflow is a detail of the definition, and six
  // mostly-empty columns would crowd out the four that identify it.
  const startTypeAttr = new BaseEntityAttrDescriptor('startType', FormControlType.DROPDOWN, 'Start Type', startConditionTypeSelectables);
  startTypeAttr.hideInTable = true;
  startTypeAttr.placeholder = 'How an instance comes into being; empty means only through /instances';

  const eventTypeAttr = new BaseEntityAttrDescriptor('eventType', FormControlType.TEXT_BOX, 'Event Type');
  eventTypeAttr.hideInTable = true;
  eventTypeAttr.placeholder = 'TRIGGERING_EVENT — e.g. order.submitted';

  const milestoneRefAttr = new BaseEntityAttrDescriptor('milestoneRef', FormControlType.TEXT_BOX, 'Milestone');
  milestoneRefAttr.hideInTable = true;
  milestoneRefAttr.placeholder = 'TIME_BASED_PRECONDITION — the milestone whose arrival triggers it';

  const preconditionExpressionAttr = new BaseEntityAttrDescriptor('preconditionExpression', FormControlType.TEXT_BOX, 'Precondition');
  preconditionExpressionAttr.hideInTable = true;
  preconditionExpressionAttr.placeholder = "TIME_BASED_PRECONDITION — PPCL guard, e.g. milestone.status == 'PASSED'";

  // Role ids, so a `RELATED_ENTITIES` picker over the role catalog: unlike the workflow's own `roles`
  // these are plain strings by contract, not a `*Use`. `WorkflowMapper` flattens what the control
  // writes back to ids.
  const authorizedRolesAttr = new BaseEntityAttrDescriptor('authorizedRoles', FormControlType.RELATED_ENTITIES, 'Authorized Roles');
  authorizedRolesAttr.linkedEntityType = WORKFLOW_ROLE_DEFINITION_ENTITY_NAME;
  authorizedRolesAttr.hideInTable = true;

  // A key/value map of context variable name to JSONPath into the event — the same control an
  // instance's `context` uses.
  const payloadMappingAttr = new BaseEntityAttrDescriptor('payloadMapping', FormControlType.ADDITIONAL_PROPERTIES, 'Payload Mapping');
  payloadMappingAttr.hideInTable = true;

  // The one part of the start condition that is a list, so the one part that is an embedded entity.
  const requiredArtifactsAttr = new BaseEntityAttrDescriptor('requiredArtifacts', FormControlType.EMBEDDED_COMPONENTS, 'Required Artifacts');
  requiredArtifactsAttr.linkedEntityType = WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME;
  requiredArtifactsAttr.referenceIdField = WORKFLOW_REQUIRED_START_ARTIFACT_ID_FIELD;
  requiredArtifactsAttr.hideInTable = true;
  // endregion

  // region the `*Use` rows — participation, not containment of the definition
  // A role, an artifact and a tool are catalog aggregates of their own, each with a list screen and
  // each shared across workflows. What the workflow holds is neither the definition nor a bare id: it
  // is a `RoleUse` / `ArtifactUse` / `ToolUse` row wrapping the definition id, which is what the
  // contract's `Workflow.roles` and its two siblings are arrays of.
  //
  // So `EMBEDDED_COMPONENTS` rather than `RELATED_ENTITIES`: the rows travel inside this entity's
  // payload and are saved with it, which the full-replacement PUT requires, while the definition they
  // name is only referenced — removing a row detaches the role, it never deletes it. The `FOREIGN_KEY`
  // inside the row is what keeps the reference navigable and pickable.
  //
  // `referenceIdField` is required on all three: a `*Use` has no `id`, so the definition id it wraps is
  // what addresses the row in the URL.
  const rolesAttr = new BaseEntityAttrDescriptor('roles', FormControlType.EMBEDDED_COMPONENTS, 'Roles');
  rolesAttr.linkedEntityType = WORKFLOW_ROLE_USE_ENTITY_NAME;
  rolesAttr.referenceIdField = WORKFLOW_ROLE_USE_ID_FIELD;
  rolesAttr.hideInTable = true;

  const artifactsAttr = new BaseEntityAttrDescriptor('artifacts', FormControlType.EMBEDDED_COMPONENTS, 'Artifacts');
  artifactsAttr.linkedEntityType = WORKFLOW_ARTIFACT_USE_ENTITY_NAME;
  artifactsAttr.referenceIdField = WORKFLOW_ARTIFACT_USE_ID_FIELD;
  artifactsAttr.hideInTable = true;

  const toolsAttr = new BaseEntityAttrDescriptor('tools', FormControlType.EMBEDDED_COMPONENTS, 'Tools');
  toolsAttr.linkedEntityType = WORKFLOW_TOOL_USE_ENTITY_NAME;
  toolsAttr.referenceIdField = WORKFLOW_TOOL_USE_ID_FIELD;
  toolsAttr.hideInTable = true;
  // endregion

  // The workflow's control flow: an assignment pairs a shared task with the one role performing it
  // *here* and with the ordering that exists only here, so it has no meaning outside this workflow.
  const tasksAttr = new BaseEntityAttrDescriptor('tasks', FormControlType.EMBEDDED_COMPONENTS, 'Tasks');
  tasksAttr.linkedEntityType = WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME;
  tasksAttr.referenceIdField = WORKFLOW_TASK_ASSIGNMENT_ID_FIELD;
  tasksAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([extendsAttr, activeInstancesAttr, versionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };
  const startConditionRow = new FlexboxDescriptor([startTypeAttr, eventTypeAttr, milestoneRefAttr, preconditionExpressionAttr], FlexDirection.ROW);
  startConditionRow.style = { 'column-gap': '10px' };
  const startConditionDetailRow = new FlexboxDescriptor([authorizedRolesAttr, payloadMappingAttr], FlexDirection.ROW);
  startConditionDetailRow.style = { 'column-gap': '10px' };

  // The five embedded lists are stacked rather than laid out in a row: each renders a table with its
  // own toolbar, and three of those side by side leaves no column wide enough to read.
  const flexBoxContainer = new FlexboxDescriptor(
    [identityRow, revisionRow, descriptionAttr, startConditionRow, startConditionDetailRow, requiredArtifactsAttr, rolesAttr, artifactsAttr, toolsAttr, tasksAttr],
    FlexDirection.COLUMN,
  );
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createWorkflowDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_ENTITY_NAME,
    attrDescriptors: createWorkflowAttrDescriptors(),
    i18nScope: WORKFLOW_I18N_SCOPE,
  });
}
