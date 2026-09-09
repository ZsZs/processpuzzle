import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { APP_MODULE_MOUNT_I18N_SCOPE } from '../base-app.i18n';
import { APP_DEFINITION_ENTITY_NAME, APP_MODULE_MOUNT_ENTITY_NAME } from './app-entity-names';

export { APP_MODULE_MOUNT_ENTITY_NAME };

/** `ModuleMount` has no `id` — an app mounts a given module once, so `moduleKey` identifies the row. */
export const APP_MODULE_MOUNT_ID_FIELD = 'moduleKey';

function createModuleMountAttrDescriptors(): AbstractAttrDescriptor[] {
  // A plain text box rather than a FOREIGN_KEY onto the module list: modules stay loosely coupled, so a
  // key naming no existing module is a server-side warning rather than an error, and a dropdown of the
  // modules authored so far would turn that warning into something the form refuses to let happen.
  const moduleKeyAttr = new BaseEntityAttrDescriptor('moduleKey', FormControlType.TEXT_BOX, 'Module Key', undefined, true);
  moduleKeyAttr.required = true;
  moduleKeyAttr.isHeading = true;
  moduleKeyAttr.placeholder = 'Key of a module definition of this organization';
  // Both patterns are the contract's own. Declaring them here makes the form refuse what the backend
  // would refuse anyway, and puts them in the e2e registry, so generated fixtures produce dashed tokens
  // rather than prose the PUT answers with 400.
  moduleKeyAttr.pattern = '^[a-z0-9]+(-[a-z0-9]+)*$';

  const basePathAttr = new BaseEntityAttrDescriptor('basePath', FormControlType.TEXT_BOX, 'Base Path');
  basePathAttr.required = true;
  basePathAttr.placeholder = 'The module routes are mounted under this prefix, e.g. claims';
  basePathAttr.pattern = '^[a-z0-9][a-z0-9\\-/]*$';

  const flexBoxContainer = new FlexboxDescriptor([moduleKeyAttr, basePathAttr], FlexDirection.ROW);
  flexBoxContainer.style = { 'column-gap': '10px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createModuleMountDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: APP_MODULE_MOUNT_ENTITY_NAME,
    attrDescriptors: createModuleMountAttrDescriptors(),
    i18nScope: APP_MODULE_MOUNT_I18N_SCOPE,
    componentParent: APP_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
