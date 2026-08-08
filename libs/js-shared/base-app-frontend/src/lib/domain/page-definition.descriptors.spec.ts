import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { APP_DEFINITION_ENTITY_NAME } from './app-entity-names';
import { APP_PAGE_ENTITY_NAME, createPageDefinitionDescriptor } from './page-definition.descriptors';
import { APP_WIDGET_ENTITY_NAME } from './widget-ref.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createPageDefinitionDescriptor', () => {
  const descriptor = createPageDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(APP_PAGE_ENTITY_NAME);
  });

  it('is an embedded component of the app definition', () => {
    expect(descriptor.componentParents).toEqual([APP_DEFINITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_app.app_page');
    expect(descriptor.i18nKey()).toBe('base_app.app_page._self');
    expect(byName('widgets')?.i18nKey()).toBe('base_app.app_page.widgets');
  });

  it('describes the route segment, the title and the page content', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'title', 'translocoId', 'widgets']);
  });

  it('requires what the router and the navigation need', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('title')?.required).toBe(true);
  });

  it('links to the details form from the title, the id being an opaque route segment', () => {
    expect(byName('title')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('title');
  });

  it('contains the widgets, which have no endpoint of their own', () => {
    expect(byName('widgets')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('widgets')?.linkedEntityType).toBe(APP_WIDGET_ENTITY_NAME);
    expect(descriptor.embeddedAttrFor(APP_WIDGET_ENTITY_NAME)?.attrName).toBe('widgets');
  });

  it('keeps the list to the identifying fields', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['id', 'title']);
  });
});
