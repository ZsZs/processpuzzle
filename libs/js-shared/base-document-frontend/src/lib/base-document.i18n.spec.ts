import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import de from '../assets/i18n/base_document/de.json';
import en from '../assets/i18n/base_document/en.json';
import es from '../assets/i18n/base_document/es.json';
import fr from '../assets/i18n/base_document/fr.json';
import hu from '../assets/i18n/base_document/hu.json';
import { BASE_DOCUMENT_TRANSLOCO_SCOPE, DOCUMENT_I18N_SCOPE, DOCUMENT_INPUT_PORT_I18N_SCOPE, DOCUMENT_OUTPUT_PORT_I18N_SCOPE } from './base-document.i18n';
import { createDocumentDescriptor } from './domain/base-document.descriptors';
import { createDocumentInputPortDescriptor, createDocumentOutputPortDescriptor } from './domain/document-port.descriptors';

/** Dotted key paths of a transloco translation file, as transloco flattens them under the scope alias. */
function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('base_document translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('derives the key roots from the scope the route registers', () => {
    expect(BASE_DOCUMENT_TRANSLOCO_SCOPE).toBe('base_document');
    expect(DOCUMENT_I18N_SCOPE).toBe('base_document.document');
    expect(DOCUMENT_INPUT_PORT_I18N_SCOPE).toBe('base_document.document_input_port');
    expect(DOCUMENT_OUTPUT_PORT_I18N_SCOPE).toBe('base_document.document_output_port');
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });

  it.each(Object.entries(languages))('leaves no key of %s empty', (_language, translations) => {
    const values = flattenKeys(translations).map((key) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], translations));

    expect(values.every((value) => typeof value === 'string' && value.trim().length > 0)).toBe(true);
  });

  /**
   * The ports have key roots of their own rather than sharing the document's: both carry a `name`, a `type`
   * and a `description`, which in one namespace would overwrite each other and the document's own labels.
   */
  it.each([
    ['document', createDocumentDescriptor()],
    ['input port', createDocumentInputPortDescriptor()],
    ['output port', createDocumentOutputPortDescriptor()],
  ])('labels the entity name and every attribute of the %s form', (_name, descriptor: BaseEntityDescriptor) => {
    const scopedKeys = [descriptor.i18nKey(), ...flattenAttrs(descriptor.attrDescriptors).map((attr) => attr.i18nKey())];

    // The descriptor keys carry the scope alias; the file itself starts one level below it.
    const expectedKeys = scopedKeys.map((key) => key?.replace(`${BASE_DOCUMENT_TRANSLOCO_SCOPE}.`, ''));
    expect(englishKeys).toEqual(expect.arrayContaining(expectedKeys as string[]));
  });
});
