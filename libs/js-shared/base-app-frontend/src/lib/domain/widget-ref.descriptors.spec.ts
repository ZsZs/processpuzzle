import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { APP_PAGE_ENTITY_NAME, APP_REGION_ENTITY_NAME } from './app-entity-names';
import { APP_WIDGET_ENTITY_NAME, createWidgetRefDescriptor } from './widget-ref.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createWidgetRefDescriptor', () => {
  const descriptor = createWidgetRefDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(APP_WIDGET_ENTITY_NAME);
  });

  it('is an embedded component of a region, a page or another widget', () => {
    expect(descriptor.componentParents).toEqual([APP_REGION_ENTITY_NAME, APP_PAGE_ENTITY_NAME, APP_WIDGET_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_app.app_widget');
    expect(descriptor.i18nKey()).toBe('base_app.app_widget._self');
    expect(byName('props')?.i18nKey()).toBe('base_app.app_widget.props');
  });

  it('describes the widget key, its configuration and its children', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'type', 'props', 'children']);
  });

  it('links to the details form from the id, two widgets of one type sharing a label otherwise', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('id');
  });

  it('leaves the registry key open, so a new widget type needs no schema change', () => {
    expect(byName('type')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('type')?.required).toBe(true);
    expect(byName('type')?.getSelectables()).toBeUndefined();
  });

  it('edits the per-type props through a key/value editor', () => {
    expect(byName('props')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
  });

  it('contains widgets of its own, nested in itself', () => {
    expect(byName('children')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('children')?.linkedEntityType).toBe(APP_WIDGET_ENTITY_NAME);
    expect(descriptor.embeddedAttrFor(APP_WIDGET_ENTITY_NAME)?.attrName).toBe('children');
  });

  it('keeps the list to the identifying fields', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['id', 'type']);
  });
});
