import { WidgetPropsAttrDescriptor, WidgetRegistration } from '@processpuzzle/widgets';
import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { APP_REGION_ENTITY_NAME, APP_ROUTE_ENTITY_NAME } from './app-entity-names';
import { APP_WIDGET_ENTITY_NAME, createWidgetInstanceDescriptor, widgetTypeSelectables } from './widget-instance.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createWidgetInstanceDescriptor', () => {
  const descriptor = createWidgetInstanceDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(APP_WIDGET_ENTITY_NAME);
  });

  it('is an embedded component of a region or of a route', () => {
    expect(descriptor.componentParents).toEqual([APP_REGION_ENTITY_NAME, APP_ROUTE_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_app.app_widget');
    expect(descriptor.i18nKey()).toBe('base_app.app_widget._self');
    expect(byName('props')?.i18nKey()).toBe('base_app.app_widget.props');
  });

  it('describes the widget key, its configuration and where it renders', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'type', 'placement', 'props']);
  });

  it('links to the details form from the id, two widgets of one type sharing a label otherwise', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('id');
  });

  it('picks the registry key from a dropdown, offering nothing when no widget types are given', () => {
    expect(byName('type')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('type')?.required).toBe(true);
    expect(byName('type')?.getSelectables()).toEqual([]);
  });

  it('offers the widget types it is given', () => {
    const typeAttr = flatten(createWidgetInstanceDescriptor([{ key: 'entity-grid', value: 'entity-grid' }]).attrDescriptors).find((attr) => attr.attrName === 'type');

    expect(typeAttr?.getSelectables()).toEqual([{ key: 'entity-grid', value: 'entity-grid' }]);
  });

  it('edits the per-type props through a form generated from the type', () => {
    expect(byName('props')?.formControlType).toBe(FormControlType.CUSTOM);
    expect(byName('props')).toBeInstanceOf(WidgetPropsAttrDescriptor);
  });

  it('offers the two placements and embeds no widget of its own', () => {
    expect(byName('placement')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(
      byName('placement')
        ?.getSelectables()
        ?.map((selectable) => selectable.key),
    ).toEqual(['STANDALONE', 'REFERENCED']);
    expect(descriptor.embeddedAttrFor(APP_WIDGET_ENTITY_NAME)).toBeUndefined();
  });

  it('keeps the list to the identifying fields', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['id', 'type']);
  });
});

describe('widgetTypeSelectables', () => {
  const registration = (type: string) => ({ type, component: class {}, definition: { name: `${type} name` } }) as WidgetRegistration;

  it('lists the registry keys, sorted, storing and showing the key rather than the display name', () => {
    const registry = new Map([['markdown', registration('markdown')], ['entity-grid', registration('entity-grid')]]);

    expect(widgetTypeSelectables(registry)).toEqual([
      { key: 'entity-grid', value: 'entity-grid' },
      { key: 'markdown', value: 'markdown' },
    ]);
  });

  it('sorts mixed-case keys alphabetically without changing the registry order', () => {
    const registry = new Map([['Markdown', registration('Markdown')], ['entity-grid', registration('entity-grid')]]);

    expect(widgetTypeSelectables(registry)).toEqual([
      { key: 'entity-grid', value: 'entity-grid' },
      { key: 'Markdown', value: 'Markdown' },
    ]);
    expect([...registry.keys()]).toEqual(['Markdown', 'entity-grid']);
  });

  it('is empty for an empty registry', () => {
    expect(widgetTypeSelectables(new Map())).toEqual([]);
  });
});
