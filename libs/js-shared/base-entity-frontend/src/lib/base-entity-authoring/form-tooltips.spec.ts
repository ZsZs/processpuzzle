import { describe, expect, it } from 'vitest';
import de from '../../assets/i18n/base_entity/de.json';
import en from '../../assets/i18n/base_entity/en.json';
import es from '../../assets/i18n/base_entity/es.json';
import fr from '../../assets/i18n/base_entity/fr.json';
import hu from '../../assets/i18n/base_entity/hu.json';
import { AbstractAttrDescriptor } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import type { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { FlexboxDescriptor } from '../base-entity/flexboxDescriptor';
import { createEntityAttributeDescriptor } from './entity-attribute.descriptors';
import { createEntityDefinitionDescriptor } from './entity-definition.descriptors';

/**
 * Every field of the authoring forms carries a tooltip, in every language the library ships — the tooltips
 * are what tell the person declaring an entity type what each field expects.
 */
describe('base-entity authoring form tooltips', () => {
  const descriptors: BaseEntityDescriptor[] = [createEntityDefinitionDescriptor(), createEntityAttributeDescriptor()];
  const keys = descriptors.flatMap((descriptor) => attributesOf(descriptor.attrDescriptors).map((attr) => attr.tooltipI18nKey()));

  it.each(Object.entries({ en, de, es, fr, hu }))('has a tooltip for every form field in %s', (_lang, translation) => {
    const missing = keys.filter((key) => typeof resolve({ base_entity: translation }, key) !== 'string');

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
