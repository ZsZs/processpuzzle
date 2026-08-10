import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { ARTIFACT_I18N_SCOPE } from '../base-artifact.i18n';
import { ARTIFACT_ENTITY_NAME, ARTIFACT_INPUT_PORT_ENTITY_NAME, ARTIFACT_OUTPUT_PORT_ENTITY_NAME } from './artifact-entity-names';

const PORT_TYPES = ['STRING', 'NUMBER', 'BOOLEAN', 'DATE', 'OBJECT', 'ARRAY', 'ENTITY_REF', 'ENTITY_COLLECTION'];
const ENTITY_PORT_TYPES = ['ENTITY_REF', 'ENTITY_COLLECTION'];

// Shared between input and output ports — everything except `required`, `defaultValue` and
// `defaultRsqlFilter`, which only make sense for a port the host has to supply a value for.
function createCommonPortAttrs(): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;

  const typeAttr = new BaseEntityAttrDescriptor('type', FormControlType.DROPDOWN, 'Type', toSelectables(PORT_TYPES));
  typeAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.hideInTable = true;

  // entityType/attributeVisibility only mean something for ENTITY_REF / ENTITY_COLLECTION —
  // shown regardless (the form has no per-value conditional-visibility mechanism today) but
  // documented as such via placeholder text, same as base-app does for widget-only fields.
  const entityTypeAttr = new BaseEntityAttrDescriptor('entityType', FormControlType.TEXT_BOX, 'Entity type');
  entityTypeAttr.placeholder = `Only used when type is one of: ${ENTITY_PORT_TYPES.join(', ')}`;
  entityTypeAttr.hideInTable = true;

  // AttributeVisibility (mode + attributes) has no dedicated FormControlType of its own yet —
  // ADDITIONAL_PROPERTIES is an honest stand-in: it's a small, rarely-touched nested object, and
  // introducing a purpose-built control for one two-field shape isn't worth it yet. Revisit if a
  // second port-like concept needs the same shape.
  const attributeVisibilityAttr = new BaseEntityAttrDescriptor('attributeVisibility', FormControlType.ADDITIONAL_PROPERTIES, 'Attribute visibility');
  attributeVisibilityAttr.hideInTable = true;

  return [nameAttr, typeAttr, descriptionAttr, entityTypeAttr, attributeVisibilityAttr];
}

function createArtifactInputPortAttrDescriptors(): AbstractAttrDescriptor[] {
  const [nameAttr, typeAttr, descriptionAttr, entityTypeAttr, attributeVisibilityAttr] = createCommonPortAttrs();

  const requiredAttr = new BaseEntityAttrDescriptor('required', FormControlType.CHECKBOX, 'Required');

  const defaultValueAttr = new BaseEntityAttrDescriptor('defaultValue', FormControlType.TEXT_BOX, 'Default value');
  defaultValueAttr.placeholder = 'Used when required is false and the host supplies nothing';
  defaultValueAttr.hideInTable = true;

  const defaultRsqlFilterAttr = new BaseEntityAttrDescriptor('defaultRsqlFilter', FormControlType.TEXT_BOX, 'Default RSQL filter');
  defaultRsqlFilterAttr.placeholder = `Only used when type is ENTITY_COLLECTION`;
  defaultRsqlFilterAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([nameAttr, typeAttr, requiredAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  return [new FlexboxDescriptor(
    [identityRow, descriptionAttr, entityTypeAttr, attributeVisibilityAttr, defaultValueAttr, defaultRsqlFilterAttr],
    FlexDirection.COLUMN,
  )];
}

function createArtifactOutputPortAttrDescriptors(): AbstractAttrDescriptor[] {
  const [nameAttr, typeAttr, descriptionAttr, entityTypeAttr, attributeVisibilityAttr] = createCommonPortAttrs();

  const identityRow = new FlexboxDescriptor([nameAttr, typeAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  return [new FlexboxDescriptor([identityRow, descriptionAttr, entityTypeAttr, attributeVisibilityAttr], FlexDirection.COLUMN)];
}

export function createArtifactInputPortDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ARTIFACT_INPUT_PORT_ENTITY_NAME,
    attrDescriptors: createArtifactInputPortAttrDescriptors(),
    i18nScope: ARTIFACT_I18N_SCOPE,
    componentParent: ARTIFACT_ENTITY_NAME,
    isEmbedded: true,
  });
}

export function createArtifactOutputPortDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ARTIFACT_OUTPUT_PORT_ENTITY_NAME,
    attrDescriptors: createArtifactOutputPortAttrDescriptors(),
    i18nScope: ARTIFACT_I18N_SCOPE,
    componentParent: ARTIFACT_ENTITY_NAME,
    isEmbedded: true,
  });
}
