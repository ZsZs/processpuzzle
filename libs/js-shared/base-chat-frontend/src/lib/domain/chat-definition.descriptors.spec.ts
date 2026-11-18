import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import de from '../../assets/i18n/base_chat/de.json';
import en from '../../assets/i18n/base_chat/en.json';
import es from '../../assets/i18n/base_chat/es.json';
import fr from '../../assets/i18n/base_chat/fr.json';
import hu from '../../assets/i18n/base_chat/hu.json';
import { createChatDefinitionDescriptor } from './chat-definition.descriptors';

function attributesOf(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => {
    if (descriptor instanceof FlexboxDescriptor) return attributesOf(descriptor.attrDescriptors);
    return descriptor instanceof BaseEntityAttrDescriptor ? [descriptor] : [];
  });
}

function resolve(translation: object, key: string | undefined): unknown {
  return key?.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown> | undefined)?.[segment], translation);
}

describe('createChatDefinitionDescriptor', () => {
  const attributes = (descriptor: BaseEntityDescriptor) => attributesOf(descriptor.attrDescriptors);
  const byName = (descriptor: BaseEntityDescriptor, name: string) => attributes(descriptor).find((attr) => attr.attrName === name);

  it('offers the entity codes as a dropdown when it is given them, and a text box otherwise', () => {
    expect(byName(createChatDefinitionDescriptor(() => []), 'subjectDefinition')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName(createChatDefinitionDescriptor(), 'subjectDefinition')?.formControlType).toBe(FormControlType.TEXT_BOX);
  });

  it('makes the key the identity and the heading, and the release state read-only', () => {
    const descriptor = createChatDefinitionDescriptor();

    expect(byName(descriptor, 'id')).toMatchObject({ required: true, isHeading: true, pattern: '^[a-z][a-z0-9-]*$' });
    expect(byName(descriptor, 'releaseState')?.disabled).toBe(true);
  });

  it('suggests the realm roles for the three role lists, and nothing for the states', () => {
    const descriptor = createChatDefinitionDescriptor();

    for (const name of ['readRoles', 'writeRoles', 'createRoles']) {
      expect(byName(descriptor, name)?.formControlType).toBe(FormControlType.TAGS);
      expect(byName(descriptor, name)?.tagSuggestions).toBeDefined();
    }
    expect(byName(descriptor, 'writableStates')?.tagSuggestions).toBeUndefined();
  });

  it.each(Object.entries({ en, de, es, fr, hu }))('has a tooltip for every form field in %s', (_lang, translation) => {
    const keys = attributes(createChatDefinitionDescriptor()).map((attr) => attr.tooltipI18nKey());
    const missing = keys.filter((key) => typeof resolve({ base_chat: translation }, key) !== 'string');

    expect(keys.length).toBeGreaterThan(10);
    expect(missing).toEqual([]);
  });
});
