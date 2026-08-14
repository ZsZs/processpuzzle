import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { APP_DEFINITION_ENTITY_NAME } from './app-entity-names';
import { APP_MODULE_MOUNT_ENTITY_NAME, APP_MODULE_MOUNT_ID_FIELD, createModuleMountDescriptor } from './module-mount.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createModuleMountDescriptor', () => {
  const descriptor = createModuleMountDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(APP_MODULE_MOUNT_ENTITY_NAME);
  });

  it('is an embedded component of the app definition', () => {
    expect(descriptor.componentParents).toEqual([APP_DEFINITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_app.app_module_mount');
    expect(descriptor.i18nKey()).toBe('base_app.app_module_mount._self');
    expect(byName('basePath')?.i18nKey()).toBe('base_app.app_module_mount.basePath');
  });

  it('describes the module it mounts and where', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['moduleKey', 'basePath']);
    expect(byName('moduleKey')?.required).toBe(true);
    expect(byName('basePath')?.required).toBe(true);
  });

  /**
   * Both values are URL material — the key addresses a module endpoint, the base path prefixes every
   * route the module contributes — so the form has to constrain them exactly as the contract does.
   * Omitting either pattern is not merely lax: the generated e2e fixtures read it from the descriptor,
   * and without it they offer prose that the backend's own `@Pattern` rejects with 400.
   */
  it('constrains both values as the contract does', () => {
    expect(byName('moduleKey')?.pattern).toBe('^[a-z0-9]+(-[a-z0-9]+)*$');
    expect(byName('basePath')?.pattern).toBe('^[a-z0-9][a-z0-9\\-/]*$');
  });

  it('identifies a mount by the module key, the schema giving it no id', () => {
    expect(APP_MODULE_MOUNT_ID_FIELD).toBe('moduleKey');
    expect(descriptor.componentIdentification()).toBe(APP_MODULE_MOUNT_ID_FIELD);
  });

  /**
   * Loose coupling made visible: a dropdown of the modules authored so far would refuse a key the
   * backend only warns about, so a mount can name a module that does not exist yet.
   */
  it('takes the module key as free text rather than a foreign key', () => {
    expect(byName('moduleKey')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('moduleKey')?.linkedEntityType).toBeUndefined();
  });

  it('carries no embedded children, a module being an aggregate of its own', () => {
    expect(descriptor.embeddedAttrDescriptors()).toEqual([]);
  });

  it('shows both fields in the list, there being nothing else to a mount', () => {
    expect(attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName)).toEqual(['moduleKey', 'basePath']);
  });
});
