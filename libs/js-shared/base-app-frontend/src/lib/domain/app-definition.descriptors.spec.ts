import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { APP_DEFINITION_ENTITY_NAME, createAppDefinitionDescriptor } from './app-definition.descriptors';
import { APP_MODULE_MOUNT_ENTITY_NAME, APP_MODULE_MOUNT_ID_FIELD } from './module-mount.descriptors';
import { APP_REGION_ENTITY_NAME, APP_REGION_ID_FIELD } from './region-definition.descriptors';
import { APP_ROUTE_ENTITY_NAME, APP_ROUTE_ID_FIELD } from './route-definition.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createAppDefinitionDescriptor', () => {
  const descriptor = createAppDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(APP_DEFINITION_ENTITY_NAME);
  });

  it('roots the labels in the library scope rather than in a top-level entity scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_app.app_definition');
    expect(descriptor.i18nKey()).toBe('base_app.app_definition._self');
    expect(byName('sidenavOpenByDefault')?.i18nKey()).toBe('base_app.app_definition.sidenavOpenByDefault');
  });

  it('describes every part of the contract: identity, revision, theme, layout and the nested graph', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual([
      'id',
      'name',
      'translocoId',
      'status',
      'version',
      'publishedVersion',
      'updatedAt',
      'description',
      'materialTheme',
      'colorScheme',
      'logoUrl',
      'faviconUrl',
      'tokenOverrides',
      'preset',
      'sidenavMode',
      'contentMaxWidth',
      'sidenavCollapsible',
      'sidenavOpenByDefault',
      'regions',
      'routes',
      'modules',
    ]);
  });

  it('requires the id and the name', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('name')?.required).toBe(true);
  });

  it('links to the details form from the name column', () => {
    expect(byName('name')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('name');
  });

  it('disables the server-assigned attributes', () => {
    expect(byName('status')?.disabled).toBe(true);
    expect(byName('version')?.disabled).toBe(true);
    expect(byName('publishedVersion')?.disabled).toBe(true);
    expect(byName('updatedAt')?.disabled).toBe(true);
  });

  it('offers the closed enums of the contract as dropdowns', () => {
    expect(byName('status')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('materialTheme')?.getSelectables()).toEqual([
      { key: 'azure-blue', value: 'azure-blue' },
      { key: 'rose-red', value: 'rose-red' },
      { key: 'magenta-violet', value: 'magenta-violet' },
      { key: 'cyan-orange', value: 'cyan-orange' },
    ]);
    expect(byName('colorScheme')?.getSelectables()?.map((selectable) => selectable.key)).toEqual(['light', 'dark', 'auto']);
    expect(byName('preset')?.getSelectables()?.map((selectable) => selectable.key)).toEqual(['sidenav-left', 'sidenav-right', 'top-nav']);
    expect(byName('sidenavMode')?.getSelectables()?.map((selectable) => selectable.key)).toEqual(['side', 'over', 'push']);
    expect(byName('status')?.getSelectables()?.map((selectable) => selectable.key)).toEqual(['DRAFT', 'PUBLISHED']);
  });

  it('edits the open token map through a key/value editor rather than a closed control', () => {
    expect(byName('tokenOverrides')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
  });

  it('contains the nested definitions, which have no endpoint of their own', () => {
    expect(byName('regions')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('regions')?.linkedEntityType).toBe(APP_REGION_ENTITY_NAME);
    expect(byName('routes')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('routes')?.linkedEntityType).toBe(APP_ROUTE_ENTITY_NAME);
    expect(byName('modules')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('modules')?.linkedEntityType).toBe(APP_MODULE_MOUNT_ENTITY_NAME);
  });

  it('identifies each nested row by its own key, none of the three schemas having an id', () => {
    expect(byName('regions')?.referenceIdField).toBe(APP_REGION_ID_FIELD);
    expect(byName('routes')?.referenceIdField).toBe(APP_ROUTE_ID_FIELD);
    expect(byName('modules')?.referenceIdField).toBe(APP_MODULE_MOUNT_ID_FIELD);
  });

  it('keeps the list to the header fields', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['id', 'name', 'status', 'version', 'publishedVersion', 'updatedAt']);
  });
});
