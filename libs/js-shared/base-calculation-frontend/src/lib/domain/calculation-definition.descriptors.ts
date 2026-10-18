import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { CALCULATION_DEFINITION_I18N_SCOPE } from '../base-calculation.i18n';
import { CALCULATION_DEFINITION_ENTITY_NAME } from './calculation-entity-names';

export { CALCULATION_DEFINITION_ENTITY_NAME };

/**
 * The Details form: the source, and what the backend read from it. Everything but the source is disabled —
 * the id, name and description are keys of the YAML, so they change by editing it, here or on the Source tab.
 */
function createCalculationDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.isHeading = true;
  idAttr.disabled = true;

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.disabled = true;

  const versionAttr = new BaseEntityAttrDescriptor('definitionVersion', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const outputAttr = new BaseEntityAttrDescriptor('outputEntity', FormControlType.TEXT_BOX, 'Output');
  outputAttr.disabled = true;

  const releaseStateAttr = new BaseEntityAttrDescriptor('releaseState', FormControlType.TEXT_BOX, 'Release');
  releaseStateAttr.disabled = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;
  descriptionAttr.disabled = true;

  // A plain text area: enough to create a definition and for small edits. The Source tab is the editor.
  const sourceAttr = new BaseEntityAttrDescriptor('source', FormControlType.TEXTAREA, 'Source');
  sourceAttr.required = true;
  sourceAttr.styleClass = 'full-width';
  sourceAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr, versionAttr, outputAttr, releaseStateAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  const container = new FlexboxDescriptor([identityRow, descriptionAttr, sourceAttr], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px', width: '100%' };
  return [container];
}

export function createCalculationDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: CALCULATION_DEFINITION_ENTITY_NAME,
    attrDescriptors: createCalculationDefinitionAttrDescriptors(),
    i18nScope: CALCULATION_DEFINITION_I18N_SCOPE,
  });
}
