import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { WIDGET_INPUT_PORT_I18N_SCOPE, WIDGET_OUTPUT_PORT_I18N_SCOPE } from '../base-widget.i18n';
import { PORT_TYPES } from './widget-definition';
import { WIDGET_DEFINITION_ENTITY_NAME, WIDGET_INPUT_PORT_ENTITY_NAME, WIDGET_OUTPUT_PORT_ENTITY_NAME } from './widget-entity-names';

/**
 * Attribute that identifies a port row in a URL segment, and that the embedded store keys its rows by. A
 * port has no `id` in the contract — `name` is what names it to the container, and what a binding refers
 * to — so both ends of that have to be told, here and on the owning `EMBEDDED_COMPONENTS` attribute.
 */
export const WIDGET_PORT_ID_FIELD = 'name';

const ENTITY_PORT_TYPES = ['ENTITY_REF', 'ENTITY_COLLECTION'];

/**
 * A knowing duplicate of `base-document-frontend`'s `document-port.descriptors.ts`, which says of itself
 * "revisit if a second port-like concept needs the same shape" — this is that second concept. It cannot be
 * shared by importing: base-document already depends on this library, so the dependency would have to run
 * backwards. Promoting the shape into base-entity would be the way to unify them, and is deliberately not
 * done for two callers. The *shapes* stay identical because both are the same `shared-api.yaml` schema.
 */
function createCommonPortAttrs(): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;

  const typeAttr = new BaseEntityAttrDescriptor('type', FormControlType.DROPDOWN, 'Type', toSelectables([...PORT_TYPES]));
  typeAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.hideInTable = true;

  // entityType/attributeVisibility only mean something for ENTITY_REF / ENTITY_COLLECTION — shown
  // regardless, since the form has no per-value conditional-visibility mechanism, and documented as such
  // through the placeholder.
  const entityTypeAttr = new BaseEntityAttrDescriptor('entityType', FormControlType.TEXT_BOX, 'Entity type');
  entityTypeAttr.placeholder = `Only used when type is one of: ${ENTITY_PORT_TYPES.join(', ')}`;
  entityTypeAttr.hideInTable = true;

  // A two-field nested object (mode + attributes) with no dedicated control of its own; the open key/value
  // editor is an honest stand-in, exactly as in base-document.
  const attributeVisibilityAttr = new BaseEntityAttrDescriptor('attributeVisibility', FormControlType.ADDITIONAL_PROPERTIES, 'Attribute visibility');
  attributeVisibilityAttr.hideInTable = true;

  return [nameAttr, typeAttr, descriptionAttr, entityTypeAttr, attributeVisibilityAttr];
}

function createWidgetInputPortAttrDescriptors(): AbstractAttrDescriptor[] {
  const [nameAttr, typeAttr, descriptionAttr, entityTypeAttr, attributeVisibilityAttr] = createCommonPortAttrs();

  const requiredAttr = new BaseEntityAttrDescriptor('required', FormControlType.CHECKBOX, 'Required');

  // `defaultValue` is `unknown` in the contract — anything JSON — and a text box stringifies whatever is
  // typed. Same trade-off, and same precedent, as base-document's port form.
  const defaultValueAttr = new BaseEntityAttrDescriptor('defaultValue', FormControlType.TEXT_BOX, 'Default value');
  defaultValueAttr.placeholder = 'Used when required is false and the container supplies nothing';
  defaultValueAttr.hideInTable = true;

  const defaultRsqlFilterAttr = new BaseEntityAttrDescriptor('defaultRsqlFilter', FormControlType.TEXT_BOX, 'Default RSQL filter');
  defaultRsqlFilterAttr.placeholder = 'Only used when type is ENTITY_COLLECTION';
  defaultRsqlFilterAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([nameAttr, typeAttr, requiredAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  return [new FlexboxDescriptor([identityRow, descriptionAttr, entityTypeAttr, attributeVisibilityAttr, defaultValueAttr, defaultRsqlFilterAttr], FlexDirection.COLUMN)];
}

function createWidgetOutputPortAttrDescriptors(): AbstractAttrDescriptor[] {
  const [nameAttr, typeAttr, descriptionAttr, entityTypeAttr, attributeVisibilityAttr] = createCommonPortAttrs();

  const identityRow = new FlexboxDescriptor([nameAttr, typeAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  return [new FlexboxDescriptor([identityRow, descriptionAttr, entityTypeAttr, attributeVisibilityAttr], FlexDirection.COLUMN)];
}

export function createWidgetInputPortDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WIDGET_INPUT_PORT_ENTITY_NAME,
    attrDescriptors: createWidgetInputPortAttrDescriptors(),
    i18nScope: WIDGET_INPUT_PORT_I18N_SCOPE,
    componentParent: WIDGET_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}

export function createWidgetOutputPortDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WIDGET_OUTPUT_PORT_ENTITY_NAME,
    attrDescriptors: createWidgetOutputPortAttrDescriptors(),
    i18nScope: WIDGET_OUTPUT_PORT_I18N_SCOPE,
    componentParent: WIDGET_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
