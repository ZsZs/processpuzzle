import { AbstractAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { EVENT_INSTANCE_I18N_SCOPE } from '../../base-workflow.i18n';
import { EventDirection } from '../definition/workflow';
import { EVENT_DEFINITION_ENTITY_NAME, EVENT_INSTANCE_ENTITY_NAME, WORKFLOW_INSTANCE_ENTITY_NAME } from '../workflow-entity-names';
import { timestampAttr } from '../timestamp-attr';
import { EventInstanceStatus } from './workflow-instance';
import { readOnlyAttr } from './read-only-attr';

export { EVENT_INSTANCE_ENTITY_NAME };

/** An `EventInstance` is identified by its own server-minted `id`. */
export const EVENT_INSTANCE_ID_FIELD = 'id';

const directionSelectables = toSelectables(Object.keys(EventDirection));
const statusSelectables = toSelectables(Object.keys(EventInstanceStatus));

function createEventInstanceAttrDescriptors(): AbstractAttrDescriptor[] {
  const eventUseIdAttr = readOnlyAttr('eventUseId', FormControlType.TEXT_BOX, 'Event', undefined, true);
  eventUseIdAttr.isHeading = true;

  const directionAttr = readOnlyAttr('direction', FormControlType.DROPDOWN, 'Direction', directionSelectables);

  // Shown in the table: WAITING is what tells a reader why the run is not moving.
  const statusAttr = readOnlyAttr('status', FormControlType.DROPDOWN, 'Status', statusSelectables);

  // A disabled `FOREIGN_KEY` into base-event's catalog, by entity name only — see EVENT_DEFINITION_ENTITY_NAME.
  const eventDefinitionIdAttr = readOnlyAttr('eventDefinitionId', FormControlType.FOREIGN_KEY, 'Catalog Event');
  eventDefinitionIdAttr.linkedEntityType = EVENT_DEFINITION_ENTITY_NAME;

  const correlationValueAttr = readOnlyAttr('correlationValue', FormControlType.TEXT_BOX, 'Correlation Value');

  const waitingSinceAttr = timestampAttr('waitingSince', 'Waiting Since');
  const occurredAtAttr = timestampAttr('occurredAt', 'Occurred At');

  // Timer catches only: when the timer fires next, and how often it has — a CYCLE fires more than once.
  const dueAtAttr = timestampAttr('dueAt', 'Due At');
  const fireCountAttr = readOnlyAttr('fireCount', FormControlType.TEXT_BOX, 'Fire Count');

  const payloadAttr = readOnlyAttr('payload', FormControlType.ADDITIONAL_PROPERTIES, 'Payload');
  payloadAttr.hideInTable = true;
  const contextContributionAttr = readOnlyAttr('contextContribution', FormControlType.ADDITIONAL_PROPERTIES, 'Context Contribution');
  contextContributionAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([eventUseIdAttr, directionAttr, statusAttr, eventDefinitionIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const deliveryRow = new FlexboxDescriptor([correlationValueAttr, waitingSinceAttr, occurredAtAttr], FlexDirection.ROW);
  deliveryRow.style = { 'column-gap': '10px' };
  const timerRow = new FlexboxDescriptor([dueAtAttr, fireCountAttr], FlexDirection.ROW);
  timerRow.style = { 'column-gap': '10px' };
  const dataRow = new FlexboxDescriptor([payloadAttr, contextContributionAttr], FlexDirection.ROW);
  dataRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, deliveryRow, timerRow, dataRow], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/** One intermediate event of a run, read-only below the instance's form. */
export function createEventInstanceDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: EVENT_INSTANCE_ENTITY_NAME,
    attrDescriptors: createEventInstanceAttrDescriptors(),
    i18nScope: EVENT_INSTANCE_I18N_SCOPE,
    componentParent: WORKFLOW_INSTANCE_ENTITY_NAME,
    isEmbedded: true,
    // Read-only by contract: the engine moves an event, nobody edits one.
    isAbstract: true,
  });
}
