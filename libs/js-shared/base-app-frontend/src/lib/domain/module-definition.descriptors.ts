import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { MODULE_DEFINITION_I18N_SCOPE } from '../base-app.i18n';
import { APP_ROUTE_ENTITY_NAME, MODULE_DEFINITION_ENTITY_NAME } from './app-entity-names';
import { APP_ROUTE_ID_FIELD } from './route-definition.descriptors';

export { MODULE_DEFINITION_ENTITY_NAME };

function createModuleDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  // The contract's `key`, renamed to `id` by ModuleDefinitionMapper because base-entity keys stores and
  // URLs on `id`. Labelled 'Key' so the designer sees the name the contract and `ModuleMount.moduleKey`
  // use. The pattern is the contract's own: the key is a URL segment of the module endpoints and the
  // prefix a mount composes with, so a value needing encoding would break both.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Key');
  idAttr.required = true;
  idAttr.pattern = '^[a-z0-9]+(-[a-z0-9]+)*$';
  idAttr.placeholder = 'Unique within the organization, e.g. claims — immutable once mounted';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;

  const translocoIdAttr = new BaseEntityAttrDescriptor('translocoId', FormControlType.TEXT_BOX, 'Transloco Id');
  translocoIdAttr.placeholder = 'Translation key of the module name';
  translocoIdAttr.hideInTable = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // Left empty means the key, which is the default the contract states — applied by
  // `moduleTranslocoScope` rather than written into the row, so an empty field stays empty.
  const translocoScopeAttr = new BaseEntityAttrDescriptor('translocoScope', FormControlType.TEXT_BOX, 'Transloco Scope');
  translocoScopeAttr.placeholder = "Scope the module's translations load under; empty means the key";
  translocoScopeAttr.hideInTable = true;

  // Server-assigned, shown so the designer can see where a module stands.
  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  // The same rows, the same descriptor and the same derived nesting as `AppDefinition.routes` — only the
  // paths are relative to the base path a mount gives them. `App Route` therefore names this entity as a
  // second `componentParent`.
  const routesAttr = new BaseEntityAttrDescriptor('routes', FormControlType.EMBEDDED_COMPONENTS, 'Routes');
  routesAttr.linkedEntityType = APP_ROUTE_ENTITY_NAME;
  routesAttr.referenceIdField = APP_ROUTE_ID_FIELD;
  routesAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr, translocoIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([translocoScopeAttr, versionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, revisionRow, descriptionAttr, routesAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createModuleDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: MODULE_DEFINITION_ENTITY_NAME,
    attrDescriptors: createModuleDefinitionAttrDescriptors(),
    i18nScope: MODULE_DEFINITION_I18N_SCOPE,
  });
}
