import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, type Selectable, toSelectables } from '@processpuzzle/base-entity';
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

/** Where the parameter form's suggestions and options come from; see {@link EventDefinitionOptions}. */
export interface EventParameterOptions {
  /** Suggested names: the attributes of a SYSTEM event's subject, which is what fills them. */
  subjectAttributes: () => Selectable[];
  /** What an `ENTITY_REF` parameter can reference. */
  referenceTypes: () => Selectable[];
}

function createEventParameterAttrDescriptors(options?: EventParameterOptions): AbstractAttrDescriptor[] {
  // What the event's occurrences, the throwers' contexts and the workflows' bindings name the value by. The
  // pattern is the contract's: an identifier, so it can be read as `<event>.<name>` out of a context. A SYSTEM
  // event fills it from the subject's attribute of the same name, and the save refuses any other, so those
  // attributes are suggested — suggested only, since a MESSAGE or SIGNAL names its parameters freely.
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', options?.subjectAttributes, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;
  nameAttr.pattern = '^[A-Za-z_][A-Za-z0-9_]*$';
  nameAttr.placeholder = 'SYSTEM — an attribute of the subject, e.g. orderNumber';

  const typeAttr = new BaseEntityAttrDescriptor('type', FormControlType.DROPDOWN, 'Type', parameterTypeSelectables);
  typeAttr.required = true;

  // Shown whatever the type — base-entity has no conditional visibility — and the label names the type it
  // belongs to. The mapper drops it for any other type. Not the attribute a SYSTEM parameter is filled from —
  // that is its name — but the type of the record an `ENTITY_REF` points at: an entity code or `document`.
  const entityTypeAttr = options
    ? new BaseEntityAttrDescriptor('entityType', FormControlType.DROPDOWN, 'Referenced Entity Type', options.referenceTypes)
    : new BaseEntityAttrDescriptor('entityType', FormControlType.TEXT_BOX, 'Referenced Entity Type');
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
export function createEventParameterDescriptor(options?: EventParameterOptions): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: EVENT_PARAMETER_ENTITY_NAME,
    attrDescriptors: createEventParameterAttrDescriptors(options),
    i18nScope: EVENT_PARAMETER_I18N_SCOPE,
    componentParent: EVENT_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
