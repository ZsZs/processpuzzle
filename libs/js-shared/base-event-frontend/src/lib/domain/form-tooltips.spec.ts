import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import de from '../../assets/i18n/base_event/de.json';
import en from '../../assets/i18n/base_event/en.json';
import es from '../../assets/i18n/base_event/es.json';
import fr from '../../assets/i18n/base_event/fr.json';
import hu from '../../assets/i18n/base_event/hu.json';
import { createEventDefinitionDescriptor } from './event-definition.descriptors';

/**
 * Every field of the event catalog's form carries a tooltip, in every language the library ships.
 */
describe('base-event form tooltips', () => {
  const descriptors: BaseEntityDescriptor[] = [createEventDefinitionDescriptor()];
  const keys = descriptors.flatMap((descriptor) => attributesOf(descriptor.attrDescriptors).map((attr) => attr.tooltipI18nKey()));

  it.each(Object.entries({ en, de, es, fr, hu }))('has a tooltip for every form field in %s', (_lang, translation) => {
    const missing = keys.filter((key) => typeof resolve({ base_event: translation }, key) !== 'string');

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
