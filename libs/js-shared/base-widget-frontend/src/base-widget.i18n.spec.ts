import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import de from './assets/i18n/base_widget/de.json';
import en from './assets/i18n/base_widget/en.json';
import es from './assets/i18n/base_widget/es.json';
import fr from './assets/i18n/base_widget/fr.json';
import hu from './assets/i18n/base_widget/hu.json';
import { BASE_WIDGET_TRANSLOCO_SCOPE, PUBLISH_BUTTON_I18N_KEY, PUBLISH_TOOLTIP_I18N_KEY, WIDGET_DEFINITION_I18N_SCOPE } from './base-widget.i18n';
import { createWidgetDefinitionDescriptor } from './widget-definition/widget-definition.descriptors';
import { createWidgetInputPortDescriptor, createWidgetOutputPortDescriptor } from './widget-definition/widget-port.descriptors';

/** Dotted key paths of a transloco translation file, as transloco flattens them under the scope alias. */
function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('base_widget translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('derives the key roots from the scope the route registers', () => {
    expect(BASE_WIDGET_TRANSLOCO_SCOPE).toBe('base_widget');
    expect(WIDGET_DEFINITION_I18N_SCOPE).toBe('base_widget.widget_definition');
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });

  it.each(Object.entries(languages))('leaves no key of %s empty', (_language, translations) => {
    const values = flattenKeys(translations).map((key) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], translations));

    expect(values.every((value) => typeof value === 'string' && value.trim().length > 0)).toBe(true);
  });

  it.each([createWidgetDefinitionDescriptor(), createWidgetInputPortDescriptor(), createWidgetOutputPortDescriptor()])('labels the entity and every attribute of $entityName', (descriptor) => {
    const scopedKeys = [descriptor.i18nKey(), ...flattenAttrs(descriptor.attrDescriptors).map((attr) => attr.i18nKey())];

    // The descriptor keys carry the scope alias; the file itself starts one level below it.
    const expectedKeys = scopedKeys.map((key) => key?.replace(`${BASE_WIDGET_TRANSLOCO_SCOPE}.`, ''));
    expect(englishKeys).toEqual(expect.arrayContaining(expectedKeys as string[]));
  });

  it('translates the Publish form action', () => {
    expect(englishKeys).toContain(PUBLISH_BUTTON_I18N_KEY.replace(`${BASE_WIDGET_TRANSLOCO_SCOPE}.`, ''));
    expect(englishKeys).toContain(PUBLISH_TOOLTIP_I18N_KEY.replace(`${BASE_WIDGET_TRANSLOCO_SCOPE}.`, ''));
  });
});
