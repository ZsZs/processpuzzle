import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { ENTITY_MODES, ROUTE_TARGET_KINDS } from './app-definition';
import { APP_DEFINITION_ENTITY_NAME, MODULE_DEFINITION_ENTITY_NAME } from './app-entity-names';
import { APP_ROUTE_ENTITY_NAME, APP_ROUTE_ID_FIELD, createRouteDefinitionDescriptor } from './route-definition.descriptors';
import { APP_WIDGET_ENTITY_NAME } from './widget-instance.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createRouteDefinitionDescriptor', () => {
  const descriptor = createRouteDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(APP_ROUTE_ENTITY_NAME);
  });

  // Two owners, uniquely in this graph: an app's `routes` and a module's are the same rows edited by the
  // same descriptor, so both have to be named here or the embedded control throws on the module's form.
  it('is an embedded component of both aggregates that own routes', () => {
    expect(descriptor.componentParents).toEqual([APP_DEFINITION_ENTITY_NAME, MODULE_DEFINITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_app.app_route');
    expect(descriptor.i18nKey()).toBe('base_app.app_route._self');
    expect(byName('kind')?.i18nKey()).toBe('base_app.app_route.kind');
  });

  it('describes the identity, the presentation and the flattened target', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['path', 'title', 'translocoId', 'icon', 'kind', 'roles', 'documentSlug', 'entityName', 'entityMode', 'rsqlFilter', 'widgets']);
  });

  it('declares no children, nesting being derived from the paths rather than authored', () => {
    expect(descriptor.embeddedAttrDescriptors().map((attr) => attr.attrName)).toEqual(['widgets']);
  });

  it('requires what the router needs', () => {
    expect(byName('path')?.required).toBe(true);
    expect(byName('title')?.required).toBe(true);
    expect(byName('kind')?.required).toBe(true);
  });

  it('identifies a row by its path, which is what a nav item resolves against', () => {
    expect(APP_ROUTE_ID_FIELD).toBe('path');
    expect(descriptor.componentIdentification()).toBe(APP_ROUTE_ID_FIELD);
  });

  /**
   * The path is a URL segment and the row's identification, so the form constrains it exactly as the
   * contract does — `:` and `/` allowed, nothing needing encoding. The generated e2e fixtures read the
   * pattern from here too; without it they offer prose the backend's `@Pattern` rejects with 400.
   */
  it('constrains the path as the contract does', () => {
    expect(byName('path')?.pattern).toBe('^[a-z0-9:][a-z0-9\\-:/]*$');
  });

  it('offers every target kind and entity mode of the contract as a dropdown', () => {
    expect(byName('kind')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('kind')?.getSelectables()?.map((selectable) => selectable.key)).toEqual([...ROUTE_TARGET_KINDS]);
    expect(byName('entityMode')?.getSelectables()?.map((selectable) => selectable.key)).toEqual([...ENTITY_MODES]);
  });

  it('contains the widgets of a WIDGETS target, which have no endpoint of their own', () => {
    expect(byName('widgets')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('widgets')?.linkedEntityType).toBe(APP_WIDGET_ENTITY_NAME);
    expect(descriptor.embeddedAttrFor(APP_WIDGET_ENTITY_NAME)?.attrName).toBe('widgets');
  });

  it('keeps the list to the identifying fields', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['path', 'title', 'kind']);
  });
});
