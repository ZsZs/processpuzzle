import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { WORKFLOW_START_EVENT_I18N_SCOPE } from '../../base-workflow.i18n';
import { EVENT_DEFINITION_ENTITY_NAME, WORKFLOW_ENTITY_NAME, WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME, WORKFLOW_ROLE_DEFINITION_ENTITY_NAME, WORKFLOW_START_EVENT_ENTITY_NAME } from '../workflow-entity-names';
import { WORKFLOW_REQUIRED_START_ARTIFACT_ID_FIELD } from './required-start-artifact.descriptors';
import { WorkflowStartConditionType } from './workflow';

export { WORKFLOW_START_EVENT_ENTITY_NAME };

const startTypeSelectables = toSelectables(Object.keys(WorkflowStartConditionType));

function createStartEventAttrDescriptors(): AbstractAttrDescriptor[] {
  // Unlike the workflow's other embedded rows, a start event has an `id` of its own — author-chosen, and
  // unique within the workflow against the task ids as well — so the row is addressed by it and no
  // `referenceIdField` is needed on the workflow's side.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique within the workflow, e.g. order-created';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.placeholder = 'What the diagram calls it; empty shows the start type';

  // Required by contract: an event that says nothing about how it fires is no event. The other fields are
  // shown whatever the type, because base-entity has no conditional-visibility mechanism and the backend
  // ignores rather than rejects the fields the chosen type does not read. The placeholders name the type
  // each belongs to, which is the honest substitute.
  const startTypeAttr = new BaseEntityAttrDescriptor('startType', FormControlType.DROPDOWN, 'Start Type', startTypeSelectables);
  startTypeAttr.required = true;

  // A reference into base-event's catalog, by entity name only — see EVENT_DEFINITION_ENTITY_NAME. The
  // value stored is the event definition's id (`OrderCreatedEvent`), which is what the backend matches
  // a published event against.
  const eventTypeAttr = new BaseEntityAttrDescriptor('eventType', FormControlType.FOREIGN_KEY, 'Event Type');
  eventTypeAttr.linkedEntityType = EVENT_DEFINITION_ENTITY_NAME;
  eventTypeAttr.hideInTable = true;
  eventTypeAttr.placeholder = 'TRIGGERING_EVENT — the catalog event, e.g. OrderCreatedEvent';

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

  // The one part of the event that is a list, so the one part that is an embedded entity — nested one
  // level further down, the way base-app's nav items nest their children.
  const requiredArtifactsAttr = new BaseEntityAttrDescriptor('requiredArtifacts', FormControlType.EMBEDDED_COMPONENTS, 'Required Artifacts');
  requiredArtifactsAttr.linkedEntityType = WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME;
  requiredArtifactsAttr.referenceIdField = WORKFLOW_REQUIRED_START_ARTIFACT_ID_FIELD;
  requiredArtifactsAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr, startTypeAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const triggerRow = new FlexboxDescriptor([eventTypeAttr, milestoneRefAttr, preconditionExpressionAttr], FlexDirection.ROW);
  triggerRow.style = { 'column-gap': '10px' };
  const detailRow = new FlexboxDescriptor([authorizedRolesAttr, payloadMappingAttr], FlexDirection.ROW);
  detailRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, triggerRow, detailRow, requiredArtifactsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * One way an instance of the workflow comes into being, embedded in the workflow.
 *
 * A row rather than fields flattened onto the workflow's form, which is what the single `startCondition`
 * it replaced was: a workflow may have several entry points now, and a list of rows is what the generic
 * screens already edit — `workflow/order-fulfillment-workflow/details/workflow-start-event/order-created/details`.
 */
export function createStartEventDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_START_EVENT_ENTITY_NAME,
    attrDescriptors: createStartEventAttrDescriptors(),
    i18nScope: WORKFLOW_START_EVENT_I18N_SCOPE,
    componentParent: WORKFLOW_ENTITY_NAME,
    isEmbedded: true,
  });
}
