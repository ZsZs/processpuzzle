import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { APP_ROUTE_ENTITY_NAME, MODULE_DEFINITION_ENTITY_NAME } from './app-entity-names';
import { createModuleDefinitionDescriptor } from './module-definition.descriptors';
import { APP_ROUTE_ID_FIELD } from './route-definition.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createModuleDefinitionDescriptor', () => {
  const descriptor = createModuleDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(MODULE_DEFINITION_ENTITY_NAME);
  });

  // Unlike the region, route and mount descriptors this one is routable: a module has endpoints of its
  // own rather than travelling inside another payload.
  it('is a routable entity rather than an embedded component', () => {
    expect(descriptor.isEmbedded).toBe(false);
    expect(descriptor.componentParents).toEqual([]);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_app.module_definition');
    expect(descriptor.i18nKey()).toBe('base_app.module_definition._self');
    expect(byName('translocoScope')?.i18nKey()).toBe('base_app.module_definition.translocoScope');
  });

  /**
   * The key is a URL segment of the module endpoints and the prefix a mount composes with, so the
   * pattern is the contract's own — a value needing encoding would break both.
   */
  it('labels the id Key and constrains it as the contract does', () => {
    expect(byName('id')?.label).toBe('Key');
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.pattern).toBe('^[a-z0-9]+(-[a-z0-9]+)*$');
  });

  it('makes the name the heading the list identifies a module by', () => {
    expect(byName('name')?.required).toBe(true);
    expect(byName('name')?.isHeading).toBe(true);
  });

  it('leaves the transloco scope optional, empty meaning the key', () => {
    expect(byName('translocoScope')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('translocoScope')?.required).toBeFalsy();
  });

  it('shows the server-assigned revision fields read-only', () => {
    expect(byName('version')?.disabled).toBe(true);
    expect(byName('updatedAt')?.disabled).toBe(true);
  });

  // The same rows and the same derived nesting as `AppDefinition.routes`; only the paths are relative
  // to the base path a mount gives them.
  it('edits its routes with the App Route descriptor', () => {
    expect(byName('routes')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('routes')?.linkedEntityType).toBe(APP_ROUTE_ENTITY_NAME);
    expect(byName('routes')?.referenceIdField).toBe(APP_ROUTE_ID_FIELD);
    expect(descriptor.embeddedAttrDescriptors().map((attr) => attr.attrName)).toEqual(['routes']);
  });

  /** A module mounts no modules: this is where route composition stops. */
  it('has no mounts of its own', () => {
    expect(byName('modules')).toBeUndefined();
  });

  it('lists a module by key and name, the rest being detail', () => {
    expect(attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName)).toEqual(['id', 'name', 'version', 'updatedAt']);
  });
});
