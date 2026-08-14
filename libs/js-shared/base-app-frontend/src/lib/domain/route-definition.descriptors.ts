import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { APP_ROUTE_I18N_SCOPE } from '../base-app.i18n';
import { ENTITY_MODES, ROUTE_TARGET_KINDS } from './app-definition';
import { APP_DEFINITION_ENTITY_NAME, APP_ROUTE_ENTITY_NAME, APP_WIDGET_ENTITY_NAME, MODULE_DEFINITION_ENTITY_NAME } from './app-entity-names';

export { APP_ROUTE_ENTITY_NAME };

/**
 * `RouteDefinition` has no `id` — `path` identifies a route, and it is what a `NavItem.routePath`
 * resolves against. Referencing attributes therefore have to set `referenceIdField = 'path'`; see the
 * `routePath` attribute of the `App Nav Item` descriptor.
 *
 * Unlike `App Region`'s `type`, this value may contain `/`, so the authoring URL that addresses one row
 * carries it percent-encoded (`app-route/claims%2Fopen/details`). Angular round-trips that, but a
 * reverse proxy that normalizes `%2F` before the SPA sees it would not — worth an e2e case once
 * multi-segment routes are authored in the testbed.
 */
export const APP_ROUTE_ID_FIELD = 'path';

function createRouteDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const pathAttr = new BaseEntityAttrDescriptor('path', FormControlType.TEXT_BOX, 'Path', undefined, true);
  pathAttr.required = true;
  pathAttr.placeholder = 'Relative to the app root, e.g. claims/open or claims/:id';
  // The contract's own pattern. Declaring it here is what makes the form reject a value the backend
  // would reject anyway — and it is serialized into the e2e registry, so generated fixtures produce a
  // dashed token instead of a prose value the PUT answers with 400.
  pathAttr.pattern = '^[a-z0-9:][a-z0-9\\-:/]*$';

  const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title');
  titleAttr.required = true;
  titleAttr.isHeading = true;

  const translocoIdAttr = new BaseEntityAttrDescriptor('translocoId', FormControlType.TEXT_BOX, 'Transloco Id');
  translocoIdAttr.placeholder = 'Translation key of the route title';
  translocoIdAttr.hideInTable = true;

  const iconAttr = new BaseEntityAttrDescriptor('icon', FormControlType.TEXT_BOX, 'Icon');
  iconAttr.placeholder = 'Material or FontAwesome icon name';
  iconAttr.hideInTable = true;

  const rolesAttr = new BaseEntityAttrDescriptor('roles', FormControlType.TAGS, 'Roles');
  rolesAttr.placeholder = 'Empty means any authenticated member of the organization';
  rolesAttr.hideInTable = true;

  // region flattened target — re-nested into `target` by AppDefinitionMapper
  // Which of the fields below is meaningful follows from `kind`; all of them are offered, and the
  // backend validator — not the form — is what rejects a combination that makes no sense. The generic
  // form has no conditional visibility, and a discriminated union is not something a descriptor can
  // express in the first place.
  const kindAttr = new BaseEntityAttrDescriptor('kind', FormControlType.DROPDOWN, 'Renders', toSelectables(ROUTE_TARGET_KINDS));
  kindAttr.required = true;

  const widgetsAttr = new BaseEntityAttrDescriptor('widgets', FormControlType.EMBEDDED_COMPONENTS, 'Widgets');
  widgetsAttr.linkedEntityType = APP_WIDGET_ENTITY_NAME;
  widgetsAttr.hideInTable = true;

  const documentSlugAttr = new BaseEntityAttrDescriptor('documentSlug', FormControlType.TEXT_BOX, 'Document Slug');
  documentSlugAttr.placeholder = 'DOCUMENT only: slug of the document to render';
  documentSlugAttr.hideInTable = true;

  const entityNameAttr = new BaseEntityAttrDescriptor('entityName', FormControlType.TEXT_BOX, 'Entity Name');
  entityNameAttr.placeholder = 'ENTITY only: name of the descriptor to generate the screen from';
  entityNameAttr.hideInTable = true;

  const entityModeAttr = new BaseEntityAttrDescriptor('entityMode', FormControlType.DROPDOWN, 'Entity Mode', toSelectables(ENTITY_MODES));
  entityModeAttr.hideInTable = true;

  const rsqlFilterAttr = new BaseEntityAttrDescriptor('rsqlFilter', FormControlType.TEXT_BOX, 'Rsql Filter');
  rsqlFilterAttr.placeholder = 'ENTITY + LIST only: RSQL the list is pre-filtered with';
  rsqlFilterAttr.hideInTable = true;
  // endregion

  const identityRow = new FlexboxDescriptor([pathAttr, titleAttr, translocoIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const presentationRow = new FlexboxDescriptor([iconAttr, kindAttr], FlexDirection.ROW);
  presentationRow.style = { 'column-gap': '10px' };
  const documentRow = new FlexboxDescriptor([documentSlugAttr], FlexDirection.ROW);
  documentRow.style = { 'column-gap': '10px' };
  const entityRow = new FlexboxDescriptor([entityNameAttr, entityModeAttr, rsqlFilterAttr], FlexDirection.ROW);
  entityRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, presentationRow, rolesAttr, documentRow, entityRow, widgetsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createRouteDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: APP_ROUTE_ENTITY_NAME,
    attrDescriptors: createRouteDefinitionAttrDescriptors(),
    i18nScope: APP_ROUTE_I18N_SCOPE,
    // Two parents, one descriptor: an app's own routes and a module's routes are the same schema, and a
    // route has to mean the same thing wherever it was authored. The module's paths are simply relative
    // to the base path its mount gives them.
    componentParent: [APP_DEFINITION_ENTITY_NAME, MODULE_DEFINITION_ENTITY_NAME],
    isEmbedded: true,
  });
}
