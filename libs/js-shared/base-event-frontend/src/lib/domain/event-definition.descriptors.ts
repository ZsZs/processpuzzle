import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { EVENT_DEFINITION_I18N_SCOPE } from '../base-event.i18n';
import { EventAction, EventKind } from './event-definition';

/**
 * Entity name of the catalog entry. Other features name it as a string — base-workflow's start event points
 * its `eventType` `FOREIGN_KEY` at it — and resolve it through `BASE_ENTITY_FACADE_REGISTRY`, so this value
 * is a cross-feature contract: renaming it silently empties their pickers.
 *
 * The route segment is `snakeCaseName()` of it, `event-definition`.
 */
export const EVENT_DEFINITION_ENTITY_NAME = 'Event Definition';

function createEventDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  // Author-chosen and the record's identity: other features store it, so it is the link into the form and
  // the heading of the status bar.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique within the organization, e.g. OrderCreatedEvent';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  const kindAttr = new BaseEntityAttrDescriptor('kind', FormControlType.DROPDOWN, 'Kind', toSelectables(Object.keys(EventKind)));
  kindAttr.required = true;

  // The subject fields are shown whatever the kind — base-entity has no conditional visibility — and the
  // placeholders name the kind they belong to. The mapper drops them for a non-SYSTEM event.
  const subjectTypeAttr = new BaseEntityAttrDescriptor('subjectType', FormControlType.TEXT_BOX, 'Subject Type');
  subjectTypeAttr.placeholder = 'SYSTEM — entity definition code, e.g. order';

  const actionAttr = new BaseEntityAttrDescriptor('action', FormControlType.DROPDOWN, 'Action', toSelectables(Object.keys(EventAction)));

  const stateAttr = new BaseEntityAttrDescriptor('state', FormControlType.TEXT_BOX, 'State');
  stateAttr.placeholder = 'STATE_CHANGED — the state entered, e.g. CONFIRMED';

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr, kindAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const subjectRow = new FlexboxDescriptor([subjectTypeAttr, actionAttr, stateAttr], FlexDirection.ROW);
  subjectRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, subjectRow, descriptionAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createEventDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: EVENT_DEFINITION_ENTITY_NAME,
    attrDescriptors: createEventDefinitionAttrDescriptors(),
    i18nScope: EVENT_DEFINITION_I18N_SCOPE,
  });
}
