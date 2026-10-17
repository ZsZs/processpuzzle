import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, type Selectable, toSelectables } from '@processpuzzle/base-entity';
import { EVENT_DEFINITION_I18N_SCOPE } from '../base-event.i18n';
import { EventAction, EventKind } from './event-definition';
import { EVENT_DEFINITION_ENTITY_NAME, EVENT_PARAMETER_ENTITY_NAME } from './event-entity-names';
import { EVENT_PARAMETER_ID_FIELD } from './event-parameter.descriptors';

export { EVENT_DEFINITION_ENTITY_NAME };

function createEventDefinitionAttrDescriptors(entityCodes?: () => Selectable[]): AbstractAttrDescriptor[] {
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
  // placeholders name the kind they belong to. The mapper drops them for a non-SYSTEM event. A dropdown of the
  // organization's entity types where the facade can supply them, a text box otherwise.
  const subjectTypeAttr = entityCodes
    ? new BaseEntityAttrDescriptor('subjectType', FormControlType.DROPDOWN, 'Subject Type', entityCodes)
    : new BaseEntityAttrDescriptor('subjectType', FormControlType.TEXT_BOX, 'Subject Type');
  subjectTypeAttr.placeholder = 'SYSTEM — entity definition code, e.g. order';

  const actionAttr = new BaseEntityAttrDescriptor('action', FormControlType.DROPDOWN, 'Action', toSelectables(Object.keys(EventAction)));

  const stateAttr = new BaseEntityAttrDescriptor('state', FormControlType.TEXT_BOX, 'State');
  stateAttr.placeholder = 'STATE_CHANGED — the state entered, e.g. CONFIRMED';

  // Whether the running application has this definition as it stands — new, changed since, or released — as
  // the backend reports it. Changed only by a release, so never edited.
  const releaseStateAttr = new BaseEntityAttrDescriptor('releaseState', FormControlType.TEXT_BOX, 'Release');
  releaseStateAttr.disabled = true;

  // Containment: the contract nests the parameters inside the definition and gives them no endpoint of
  // their own, so they travel inside this entity's payload and are saved with it.
  const parametersAttr = new BaseEntityAttrDescriptor('parameters', FormControlType.EMBEDDED_COMPONENTS, 'Parameters');
  parametersAttr.linkedEntityType = EVENT_PARAMETER_ENTITY_NAME;
  parametersAttr.referenceIdField = EVENT_PARAMETER_ID_FIELD;
  parametersAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr, kindAttr, releaseStateAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const subjectRow = new FlexboxDescriptor([subjectTypeAttr, actionAttr, stateAttr], FlexDirection.ROW);
  subjectRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, subjectRow, descriptionAttr, parametersAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/** @param entityCodes the entity types Subject Type offers; see {@link EventDefinitionOptions.entityCodes} */
export function createEventDefinitionDescriptor(entityCodes?: () => Selectable[]): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: EVENT_DEFINITION_ENTITY_NAME,
    attrDescriptors: createEventDefinitionAttrDescriptors(entityCodes),
    i18nScope: EVENT_DEFINITION_I18N_SCOPE,
  });
}
