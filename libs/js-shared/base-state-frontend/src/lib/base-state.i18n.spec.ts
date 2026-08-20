import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import de from '../assets/i18n/base_state/de.json';
import en from '../assets/i18n/base_state/en.json';
import es from '../assets/i18n/base_state/es.json';
import fr from '../assets/i18n/base_state/fr.json';
import hu from '../assets/i18n/base_state/hu.json';
import { BASE_STATE_TRANSLATION_SOURCE, BASE_STATE_TRANSLOCO_SCOPE, STATE_MACHINE_DEFINITION_I18N_SCOPE } from './base-state.i18n';
import { createActionRefDescriptor, createGuardRefDescriptor } from './domain/bean-ref.descriptors';
import { createStateMachineDefinitionDescriptor } from './domain/state-machine-definition.descriptors';
import { createStateDescriptor } from './domain/state.descriptors';
import { createTransitionDescriptor } from './domain/transition.descriptors';

/** Dotted key paths of a transloco translation file, as transloco flattens them under the scope alias. */
function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('base_state translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('derives the key roots from the scope the route registers', () => {
    expect(BASE_STATE_TRANSLOCO_SCOPE).toBe('base_state');
    expect(STATE_MACHINE_DEFINITION_I18N_SCOPE).toBe('base_state.state_machine_definition');
  });

  it('claims its own scope and names the backend that serves it', () => {
    expect(BASE_STATE_TRANSLATION_SOURCE.scopes).toEqual([BASE_STATE_TRANSLOCO_SCOPE]);
    expect(BASE_STATE_TRANSLATION_SOURCE.serviceRootKey).toBe('STATE_SERVICE_ROOT');
    expect(BASE_STATE_TRANSLATION_SOURCE.segment).toBe('state');
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });

  it.each(Object.entries(languages))('leaves no key of %s empty', (_language, translations) => {
    const values = flattenKeys(translations).map((key) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], translations));

    expect(values.every((value) => typeof value === 'string' && value.trim().length > 0)).toBe(true);
  });

  /**
   * Every level of the machine, not just the root: the embedded branches register no scope of their own,
   * so an unlabelled attribute of a state or a guard would render its raw key with nothing to fall back on.
   */
  it.each([
    ['definition', createStateMachineDefinitionDescriptor()],
    ['state', createStateDescriptor()],
    ['transition', createTransitionDescriptor()],
    ['guard', createGuardRefDescriptor()],
    ['action', createActionRefDescriptor()],
  ])('labels the entity and every attribute of the %s form', (_level, descriptor: BaseEntityDescriptor) => {
    const scopedKeys = [descriptor.i18nKey(), ...flattenAttrs(descriptor.attrDescriptors).map((attr) => attr.i18nKey())];

    // The descriptor keys carry the scope alias; the file itself starts one level below it.
    const expectedKeys = scopedKeys.map((key) => key?.replace(`${BASE_STATE_TRANSLOCO_SCOPE}.`, ''));
    expect(englishKeys).toEqual(expect.arrayContaining(expectedKeys as string[]));
  });
});
