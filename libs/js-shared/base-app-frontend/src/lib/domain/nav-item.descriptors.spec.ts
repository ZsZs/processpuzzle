import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { APP_REGION_ENTITY_NAME } from './app-entity-names';
import { APP_NAV_ITEM_ENTITY_NAME, createNavItemDescriptor } from './nav-item.descriptors';
import { APP_PAGE_ENTITY_NAME } from './page-definition.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createNavItemDescriptor', () => {
  const descriptor = createNavItemDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(APP_NAV_ITEM_ENTITY_NAME);
  });

  it('is an embedded component of a region or of another nav item', () => {
    expect(descriptor.componentParents).toEqual([APP_REGION_ENTITY_NAME, APP_NAV_ITEM_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
    expect(descriptor.isComponentOf(APP_NAV_ITEM_ENTITY_NAME)).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_app.app_nav_item');
    expect(descriptor.i18nKey()).toBe('base_app.app_nav_item._self');
    expect(byName('pageId')?.i18nKey()).toBe('base_app.app_nav_item.pageId');
  });

  it('describes the label, the target and the visibility of a navigation entry', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'label', 'translocoId', 'icon', 'pageId', 'roles', 'children']);
  });

  it('links to the details form from the label', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('label')?.required).toBe(true);
    expect(byName('label')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('label');
  });

  it('leaves the page optional, a group node having children instead of a target', () => {
    expect(byName('pageId')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('pageId')?.linkedEntityType).toBe(APP_PAGE_ENTITY_NAME);
    expect(byName('pageId')?.required).toBeFalsy();
  });

  it('nests navigation entries in themselves', () => {
    expect(byName('children')?.formControlType).toBe(FormControlType.RELATED_ENTITIES);
    expect(byName('children')?.linkedEntityType).toBe(APP_NAV_ITEM_ENTITY_NAME);
  });

  it('collects the authorizing roles as tags', () => {
    expect(byName('roles')?.formControlType).toBe(FormControlType.TAGS);
  });

  it('keeps the list to the identifying fields and the target', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['id', 'label', 'pageId']);
  });
});
