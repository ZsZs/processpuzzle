import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import de from '../assets/i18n/base_event/de.json';
import en from '../assets/i18n/base_event/en.json';
import es from '../assets/i18n/base_event/es.json';
import fr from '../assets/i18n/base_event/fr.json';
import hu from '../assets/i18n/base_event/hu.json';
import { BASE_EVENT_TRANSLATION_SOURCE, BASE_EVENT_TRANSLOCO_SCOPE, EVENT_DEFINITION_I18N_SCOPE } from './base-event.i18n';
import { createEventDefinitionDescriptor } from './domain/event-definition.descriptors';

function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('base_event translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('derives the key roots from the scope the route registers', () => {
    expect(BASE_EVENT_TRANSLOCO_SCOPE).toBe('base_event');
    expect(EVENT_DEFINITION_I18N_SCOPE).toBe('base_event.event_definition');
  });

  it('claims its own scope and names the backend root that would serve it', () => {
    expect(BASE_EVENT_TRANSLATION_SOURCE).toEqual({ scopes: ['base_event'], serviceRootKey: 'EVENT_SERVICE_ROOT', segment: 'event' });
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });

  it.each(Object.entries(languages))('leaves no key of %s empty', (_language, translations) => {
    const values = flattenKeys(translations).map((key) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], translations));

    expect(values.every((value) => typeof value === 'string' && value.trim().length > 0)).toBe(true);
  });

  it('labels the entity and every attribute of the form', () => {
    const descriptor = createEventDefinitionDescriptor();
    const scopedKeys = [descriptor.i18nKey(), ...flattenAttrs(descriptor.attrDescriptors).map((attr) => attr.i18nKey())];

    expect(englishKeys).toEqual(expect.arrayContaining(scopedKeys.map((key) => key?.replace(`${BASE_EVENT_TRANSLOCO_SCOPE}.`, '')) as string[]));
  });
});
