import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import de from '../assets/i18n/base_widget/de.json';
import en from '../assets/i18n/base_widget/en.json';
import es from '../assets/i18n/base_widget/es.json';
import fr from '../assets/i18n/base_widget/fr.json';
import hu from '../assets/i18n/base_widget/hu.json';
import { createWidgetDefinitionDescriptor } from './widget-definition.descriptors';
import { createWidgetInputPortDescriptor, createWidgetOutputPortDescriptor } from './widget-port.descriptors';

/**
 * Every field of the widget designer's forms carries a tooltip, in every language the library ships — the
 * tooltips are what tell the person defining a widget type what each field expects.
 */
describe('base-widget form tooltips', () => {
  const descriptors: BaseEntityDescriptor[] = [createWidgetDefinitionDescriptor(), createWidgetInputPortDescriptor(), createWidgetOutputPortDescriptor()];
  const keys = descriptors.flatMap((descriptor) => attributesOf(descriptor.attrDescriptors).map((attr) => attr.tooltipI18nKey()));

  it.each(Object.entries({ en, de, es, fr, hu }))('has a tooltip for every form field in %s', (_lang, translation) => {
    const missing = keys.filter((key) => typeof resolve({ base_widget: translation }, key) !== 'string');

    expect(keys.length).toBeGreaterThan(0);
    expect(missing).toEqual([]);
  });
});

function attributesOf(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => {
    if (descriptor instanceof FlexboxDescriptor) return attributesOf(descriptor.attrDescriptors);
    return descriptor instanceof BaseEntityAttrDescriptor ? [descriptor] : [];
  });
}

function resolve(translation: object, key: string | undefined): unknown {
  return key?.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown> | undefined)?.[segment], translation);
}
