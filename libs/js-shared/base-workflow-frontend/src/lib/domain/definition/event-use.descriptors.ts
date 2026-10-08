import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { WORKFLOW_EVENT_USE_I18N_SCOPE } from '../../base-workflow.i18n';
import { EVENT_DEFINITION_ENTITY_NAME, WORKFLOW_ENTITY_NAME, WORKFLOW_EVENT_USE_ENTITY_NAME } from '../workflow-entity-names';
import { EventDirection, JoinType } from './workflow';

export { WORKFLOW_EVENT_USE_ENTITY_NAME };

const directionSelectables = toSelectables(Object.keys(EventDirection));
const joinTypeSelectables = toSelectables(Object.keys(JoinType));

function createEventUseAttrDescriptors(): AbstractAttrDescriptor[] {
  // An id of its own, like a start event's — author-chosen and unique within the workflow against the task
  // and start-event ids, because `dependsOn` names tasks and events alike.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique within the workflow, e.g. invoice-issued';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.placeholder = 'What the diagram calls it; empty shows the catalog event';

  const directionAttr = new BaseEntityAttrDescriptor('direction', FormControlType.DROPDOWN, 'Direction', directionSelectables);
  directionAttr.required = true;

  // A reference into base-event's catalog, by entity name only — see EVENT_DEFINITION_ENTITY_NAME. The backend
  // refuses a THROW of a SYSTEM event, which only the platform raises.
  const eventDefinitionIdAttr = new BaseEntityAttrDescriptor('eventDefinitionId', FormControlType.FOREIGN_KEY, 'Event');
  eventDefinitionIdAttr.linkedEntityType = EVENT_DEFINITION_ENTITY_NAME;
  eventDefinitionIdAttr.required = true;

  // A chip list for the reason the task assignment's is one: it names sibling rows of the form being edited.
  const dependsOnAttr = new BaseEntityAttrDescriptor('dependsOn', FormControlType.TAGS, 'Depends On');
  dependsOnAttr.placeholder = 'Task or event ids that must be done first; empty means reached at start';
  dependsOnAttr.hideInTable = true;

  const joinTypeAttr = new BaseEntityAttrDescriptor('joinType', FormControlType.DROPDOWN, 'Join Type', joinTypeSelectables);
  joinTypeAttr.hideInTable = true;

  const correlationKeyAttr = new BaseEntityAttrDescriptor('correlationKey', FormControlType.TEXT_BOX, 'Correlation Key');
  correlationKeyAttr.placeholder = 'MESSAGE only — the context variable to correlate on, e.g. orderId';

  // CATCH: context variable → path into the occurred event. THROW: payload attribute → path into the context.
  const payloadMappingAttr = new BaseEntityAttrDescriptor('payloadMapping', FormControlType.ADDITIONAL_PROPERTIES, 'Payload Mapping');
  payloadMappingAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr, directionAttr, eventDefinitionIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const flowRow = new FlexboxDescriptor([dependsOnAttr, joinTypeAttr, correlationKeyAttr], FlexDirection.ROW);
  flowRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, flowRow, payloadMappingAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * One intermediate event of the workflow, embedded in it and addressed by its own id —
 * `workflow/order-fulfillment-workflow/details/workflow-event-use/invoice-issued/details`.
 */
export function createEventUseDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_EVENT_USE_ENTITY_NAME,
    attrDescriptors: createEventUseAttrDescriptors(),
    i18nScope: WORKFLOW_EVENT_USE_I18N_SCOPE,
    componentParent: WORKFLOW_ENTITY_NAME,
    isEmbedded: true,
  });
}
