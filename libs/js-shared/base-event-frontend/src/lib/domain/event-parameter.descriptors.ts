import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { EVENT_PARAMETER_I18N_SCOPE } from '../base-event.i18n';
import { EventParameterType } from './event-definition';
import { EVENT_DEFINITION_ENTITY_NAME, EVENT_PARAMETER_ENTITY_NAME } from './event-entity-names';

export { EVENT_PARAMETER_ENTITY_NAME };

/**
 * An `EventParameter` has no `id` — its `name`, unique within the definition, identifies it. The referencing
 * attribute therefore has to set `referenceIdField`; see the `parameters` attribute of the `Event Definition`
 * descriptor.
 */
export const EVENT_PARAMETER_ID_FIELD = 'name';

const parameterTypeSelectables = toSelectables(Object.keys(EventParameterType));

function createEventParameterAttrDescriptors(): AbstractAttrDescriptor[] {
  // What the event's occurrences, the throwers' contexts and the workflows' bindings name the value by. The
  // pattern is the contract's: an identifier, so it can be read as `<event>.<name>` out of a context.
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;
  nameAttr.pattern = '^[A-Za-z_][A-Za-z0-9_]*$';
  nameAttr.placeholder = 'Unique within the event, e.g. invoiceNumber';

  const typeAttr = new BaseEntityAttrDescriptor('type', FormControlType.DROPDOWN, 'Type', parameterTypeSelectables);
  typeAttr.required = true;

  // Shown whatever the type — base-entity has no conditional visibility — and the placeholder names the type
  // it belongs to. The mapper drops it for any other type. Plain text, like the definition's subject type:
  // the value is an entity definition code or `document`, and no single store offers both.
  const entityTypeAttr = new BaseEntityAttrDescriptor('entityType', FormControlType.TEXT_BOX, 'Entity Type');
  entityTypeAttr.placeholder = 'ENTITY_REF — entity definition code, e.g. order';

  const requiredAttr = new BaseEntityAttrDescriptor('required', FormControlType.CHECKBOX, 'Required');

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([nameAttr, typeAttr, entityTypeAttr, requiredAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, descriptionAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/** One value an occurrence of the event carries, embedded in the event definition. */
export function createEventParameterDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: EVENT_PARAMETER_ENTITY_NAME,
    attrDescriptors: createEventParameterAttrDescriptors(),
    i18nScope: EVENT_PARAMETER_I18N_SCOPE,
    componentParent: EVENT_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
