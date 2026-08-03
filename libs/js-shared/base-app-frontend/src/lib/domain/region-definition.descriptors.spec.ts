import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { APP_NAV_ITEM_ENTITY_NAME } from './nav-item.descriptors';
import { APP_REGION_ENTITY_NAME, APP_REGION_ID_FIELD, createRegionDefinitionDescriptor } from './region-definition.descriptors';
import { APP_WIDGET_ENTITY_NAME } from './widget-ref.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createRegionDefinitionDescriptor', () => {
  const descriptor = createRegionDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(APP_REGION_ENTITY_NAME);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_app.app_region');
    expect(descriptor.i18nKey()).toBe('base_app.app_region._self');
    expect(byName('navItems')?.i18nKey()).toBe('base_app.app_region.navItems');
  });

  it('describes the shell slot and both kinds of content it can hold', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['type', 'navItems', 'widgets']);
  });

  it('identifies a region by the slot it fills, the schema giving it no id', () => {
    expect(byName('type')?.required).toBe(true);
    expect(byName('type')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe(APP_REGION_ID_FIELD);
  });

  it('offers the closed region types of the contract as a dropdown', () => {
    expect(byName('type')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('type')?.getSelectables()?.map((selectable) => selectable.key)).toEqual(['header', 'sidenav', 'content', 'footer']);
  });

  it('links the nested definitions to their own descriptors', () => {
    expect(byName('navItems')?.formControlType).toBe(FormControlType.COMPONENTS);
    expect(byName('navItems')?.linkedEntityType).toBe(APP_NAV_ITEM_ENTITY_NAME);
    expect(byName('widgets')?.formControlType).toBe(FormControlType.COMPONENTS);
    expect(byName('widgets')?.linkedEntityType).toBe(APP_WIDGET_ENTITY_NAME);
  });

  it('keeps the list to the slot itself', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['type']);
  });
});
