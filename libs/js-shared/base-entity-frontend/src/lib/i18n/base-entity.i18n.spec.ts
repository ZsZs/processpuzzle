import { describe, expect, it } from 'vitest';
import de from '../../assets/i18n/base_entity/de.json';
import en from '../../assets/i18n/base_entity/en.json';
import es from '../../assets/i18n/base_entity/es.json';
import fr from '../../assets/i18n/base_entity/fr.json';
import hu from '../../assets/i18n/base_entity/hu.json';
import { AbstractAttrDescriptor } from '../base-entity/abstact-attr.descriptor';
import type { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import type { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { FlexboxDescriptor } from '../base-entity/flexboxDescriptor';
import { createEntityAttributeDescriptor } from '../base-entity-authoring/entity-attribute.descriptors';
import { createEntityDefinitionDescriptor } from '../base-entity-authoring/entity-definition.descriptors';
import { BASE_ENTITY_TRANSLATION_SOURCE, BASE_ENTITY_TRANSLOCO_SCOPE, ENTITY_ATTRIBUTE_I18N_SCOPE, ENTITY_DEFINITION_I18N_SCOPE } from './base-entity.i18n';

/** Dotted key paths of a transloco translation file, as transloco flattens them under the scope alias. */
function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('base_entity translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('derives the key roots from the scope the routes register', () => {
    expect(BASE_ENTITY_TRANSLOCO_SCOPE).toBe('base_entity');
    expect(ENTITY_DEFINITION_I18N_SCOPE).toBe('base_entity.entity_definition');
    expect(ENTITY_ATTRIBUTE_I18N_SCOPE).toBe('base_entity.entity_attribute');
  });

  it('claims its own scope and names the backend that serves it', () => {
    expect(BASE_ENTITY_TRANSLATION_SOURCE.scopes).toEqual([BASE_ENTITY_TRANSLOCO_SCOPE]);
    expect(BASE_ENTITY_TRANSLATION_SOURCE.serviceRootKey).toBe('ENTITY_SERVICE_ROOT');
    expect(BASE_ENTITY_TRANSLATION_SOURCE.segment).toBe('entity');
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });

  it.each(Object.entries(languages))('leaves no key of %s empty', (_language, translations) => {
    const values = flattenKeys(translations).map((key) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], translations));

    expect(values.every((value) => typeof value === 'string' && value.trim().length > 0)).toBe(true);
  });

  /**
   * Both levels of the authoring branch, not just the definition: the embedded branch registers no scope of
   * its own, so an unlabelled attribute of an `Entity Attribute` would render its raw key with nothing to
   * fall back on.
   */
  it.each([
    ['definition', createEntityDefinitionDescriptor()],
    ['attribute', createEntityAttributeDescriptor()],
  ])('labels the entity and every attribute of the %s form', (_level, descriptor: BaseEntityDescriptor) => {
    const scopedKeys = [descriptor.i18nKey(), ...flattenAttrs(descriptor.attrDescriptors).map((attr) => attr.i18nKey())];

    scopedKeys.forEach((key) => {
      expect(key).toBeDefined();
      expect(englishKeys).toContain(key?.replace(`${BASE_ENTITY_TRANSLOCO_SCOPE}.`, ''));
    });
  });
});
