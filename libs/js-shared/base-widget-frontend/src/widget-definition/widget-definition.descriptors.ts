import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { WIDGET_DEFINITION_I18N_SCOPE } from '../base-widget.i18n';
import { WIDGET_DEFINITION_STATUSES } from './widget-definition';
import { WIDGET_DEFINITION_ENTITY_NAME, WIDGET_INPUT_PORT_ENTITY_NAME, WIDGET_OUTPUT_PORT_ENTITY_NAME } from './widget-entity-names';
import { WIDGET_PORT_ID_FIELD } from './widget-port.descriptors';

export { WIDGET_DEFINITION_ENTITY_NAME };

/** The contract's `WidgetKey` pattern. Its `minLength` / `maxLength` have no descriptor equivalent. */
export const WIDGET_KEY_PATTERN = '^[a-z0-9]+(-[a-z0-9]+)*$';

function createWidgetDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  // The contract's `key`, renamed to `id` by WidgetDefinitionMapper because base-entity keys stores and
  // URLs on `id`. Labelled 'Key' so the designer sees the name the contract and every stored
  // `WidgetInstance.type` use. The pattern is the contract's own — the key is a URL segment of the widget
  // endpoints, and it is what a placement references, so a value needing encoding would break both.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Key');
  idAttr.required = true;
  idAttr.pattern = WIDGET_KEY_PATTERN;
  idAttr.placeholder = 'Semantic and kebab-case, e.g. cards-grid — immutable once instances reference it';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;

  const translocoIdAttr = new BaseEntityAttrDescriptor('translocoId', FormControlType.TEXT_BOX, 'Transloco Id');
  translocoIdAttr.placeholder = 'Translation key of the name; preferred over Name when present';
  translocoIdAttr.hideInTable = true;

  // Free-form by contract rather than an enum: which palette groupings are useful differs per
  // organization, and a wrong-but-fixable grouping is cheaper than a schema migration.
  const categoryAttr = new BaseEntityAttrDescriptor('category', FormControlType.TEXT_BOX, 'Category');
  categoryAttr.placeholder = 'Palette grouping, e.g. Content, Data, Navigation';

  const iconAttr = new BaseEntityAttrDescriptor('icon', FormControlType.TEXT_BOX, 'Icon');
  iconAttr.placeholder = 'Material icon name shown in the palette, e.g. grid_view';
  iconAttr.hideInTable = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // Server-assigned, shown so the designer can see where a definition stands, never edited here.
  const statusAttr = new BaseEntityAttrDescriptor('status', FormControlType.DROPDOWN, 'Status', toSelectables([...WIDGET_DEFINITION_STATUSES]));
  statusAttr.disabled = true;

  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const publishedVersionAttr = new BaseEntityAttrDescriptor('publishedVersion', FormControlType.TEXT_BOX, 'Published Version');
  publishedVersionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  const inputPortsAttr = new BaseEntityAttrDescriptor('inputPorts', FormControlType.EMBEDDED_COMPONENTS, 'Input ports');
  inputPortsAttr.linkedEntityType = WIDGET_INPUT_PORT_ENTITY_NAME;
  inputPortsAttr.referenceIdField = WIDGET_PORT_ID_FIELD;
  inputPortsAttr.hideInTable = true;

  const outputPortsAttr = new BaseEntityAttrDescriptor('outputPorts', FormControlType.EMBEDDED_COMPONENTS, 'Output ports');
  outputPortsAttr.linkedEntityType = WIDGET_OUTPUT_PORT_ENTITY_NAME;
  outputPortsAttr.referenceIdField = WIDGET_PORT_ID_FIELD;
  outputPortsAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr, translocoIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const paletteRow = new FlexboxDescriptor([categoryAttr, iconAttr], FlexDirection.ROW);
  paletteRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([statusAttr, versionAttr, publishedVersionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, paletteRow, revisionRow, descriptionAttr, inputPortsAttr, outputPortsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * The authoring form of a widget *type*.
 *
 * **`propsSchema` is deliberately not on it.** It is arbitrary, nested JSON Schema, and the only control
 * that could stand in for it — `ADDITIONAL_PROPERTIES` — is a flat key/value editor that would flatten a
 * schema into something the widget no longer describes. Omitting it is safe rather than lossy: the form
 * saves `{ ...loadedEntity, ...formValue }` and `WidgetDefinitionMapper` carries the field in both
 * directions, so it survives an unrelated Save untouched — and that mapper invariant is what makes this
 * choice safe, which is why its spec asserts the round trip. base-document omits `blocks` from the document
 * form for the same reason, and edits it on a tab of its own; a schema editor here is the equivalent, and
 * the next step for it is the tabs phase of the AppDefinition plan.
 */
export function createWidgetDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WIDGET_DEFINITION_ENTITY_NAME,
    attrDescriptors: createWidgetDefinitionAttrDescriptors(),
    i18nScope: WIDGET_DEFINITION_I18N_SCOPE,
  });
}
