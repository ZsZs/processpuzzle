import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { PROCESS_DEFINITION_I18N_SCOPE } from '../../base-workflow.i18n';
import {
  ARTIFACT_DEFINITION_ENTITY_NAME,
  PROCESS_DEFINITION_ENTITY_NAME,
  PROCESS_TASK_ASSIGNMENT_ENTITY_NAME,
  TOOL_DEFINITION_ENTITY_NAME,
  WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
} from '../workflow-entity-names';
import { PROCESS_TASK_ASSIGNMENT_ID_FIELD } from './process-task-assignment.descriptors';

export { PROCESS_DEFINITION_ENTITY_NAME };

function createProcessDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  // The business key *and* the record's identity: the contract addresses a process by the
  // author-chosen id, which is what the generic screens address a record by too — so unlike
  // base-state's `StateMachineDefinition`, nothing has to be mirrored here. `isLinkToDetails`, so the
  // list's own column opens the process, and `isHeading`, so the form and the status bar name it.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique per organization, e.g. order-fulfillment-workflow';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // A process this one inherits roles, artifacts and task assignments from — another row of *this*
  // list, so a real reference the framework can resolve and navigate to. Same shape as base-rule's
  // `extendsRuleId`, and the backend's `ProcessDefinitionExtendsValidator` refuses a cycle.
  const extendsAttr = new BaseEntityAttrDescriptor('extends', FormControlType.FOREIGN_KEY, 'Extends');
  extendsAttr.linkedEntityType = PROCESS_DEFINITION_ENTITY_NAME;
  extendsAttr.hideInTable = true;

  // Server-assigned: shown so the author can see where a definition stands, never edited here.
  // `activeInstances` is why the list endpoint returns the full entity rather than a summary — see
  // the contract's note on `listProcessDefinitions`.
  const activeInstancesAttr = new BaseEntityAttrDescriptor('activeInstances', FormControlType.TEXT_BOX, 'Active Instances');
  activeInstancesAttr.disabled = true;

  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  // region referenced catalog entities — association, not containment
  // A role, an artifact and a tool are catalog aggregates of their own, each with a list screen and
  // each shared across processes, so what the process holds is an *id list*: adding a row picks an
  // existing record from that record's own list, and removing one only detaches the reference — it
  // never deletes the role. That is exactly the contract between `RELATED_ENTITIES` and `COMPONENTS`,
  // and the reason none of these three is `EMBEDDED_COMPONENTS` any more.
  //
  // `referenceIdField` is left at its default: all three are keyed by `id`. `ProcessDefinitionMapper`
  // is what flattens the whole entities the control writes on selection back to the ids the contract
  // wants.
  const rolesAttr = new BaseEntityAttrDescriptor('roles', FormControlType.RELATED_ENTITIES, 'Roles');
  rolesAttr.linkedEntityType = WORKFLOW_ROLE_DEFINITION_ENTITY_NAME;
  rolesAttr.hideInTable = true;

  const artifactsAttr = new BaseEntityAttrDescriptor('artifacts', FormControlType.RELATED_ENTITIES, 'Artifacts');
  artifactsAttr.linkedEntityType = ARTIFACT_DEFINITION_ENTITY_NAME;
  artifactsAttr.hideInTable = true;

  const toolsAttr = new BaseEntityAttrDescriptor('tools', FormControlType.RELATED_ENTITIES, 'Tools');
  toolsAttr.linkedEntityType = TOOL_DEFINITION_ENTITY_NAME;
  toolsAttr.hideInTable = true;
  // endregion

  // Containment, and the only containment left on this entity: an assignment pairs a shared task with
  // the one role performing it *here* and with the ordering that exists only here, so it has no
  // meaning outside this process. The rows travel inside this entity's payload and are saved with it,
  // which the full-replacement PUT requires.
  const tasksAttr = new BaseEntityAttrDescriptor('tasks', FormControlType.EMBEDDED_COMPONENTS, 'Tasks');
  tasksAttr.linkedEntityType = PROCESS_TASK_ASSIGNMENT_ENTITY_NAME;
  tasksAttr.referenceIdField = PROCESS_TASK_ASSIGNMENT_ID_FIELD;
  tasksAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([extendsAttr, activeInstancesAttr, versionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };
  const referenceRow = new FlexboxDescriptor([rolesAttr, artifactsAttr, toolsAttr], FlexDirection.ROW);
  referenceRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, revisionRow, descriptionAttr, referenceRow, tasksAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createProcessDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: PROCESS_DEFINITION_ENTITY_NAME,
    attrDescriptors: createProcessDefinitionAttrDescriptors(),
    i18nScope: PROCESS_DEFINITION_I18N_SCOPE,
  });
}
