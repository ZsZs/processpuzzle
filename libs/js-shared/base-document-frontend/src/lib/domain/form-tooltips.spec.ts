import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import de from '../../assets/i18n/base_document/de.json';
import en from '../../assets/i18n/base_document/en.json';
import es from '../../assets/i18n/base_document/es.json';
import fr from '../../assets/i18n/base_document/fr.json';
import hu from '../../assets/i18n/base_document/hu.json';
import { createDocumentDescriptor } from './base-document.descriptors';
import { createDocumentInputPortDescriptor, createDocumentOutputPortDescriptor } from './document-port.descriptors';

/**
 * Every field of the document's property form and of its port forms carries a tooltip, in every language
 * the library ships.
 */
describe('base-document form tooltips', () => {
  const descriptors: BaseEntityDescriptor[] = [createDocumentDescriptor(), createDocumentInputPortDescriptor(), createDocumentOutputPortDescriptor()];
  const keys = descriptors.flatMap((descriptor) => attributesOf(descriptor.attrDescriptors).map((attr) => attr.tooltipI18nKey()));

  it.each(Object.entries({ en, de, es, fr, hu }))('has a tooltip for every form field in %s', (_lang, translation) => {
    const missing = keys.filter((key) => typeof resolve({ base_document: translation }, key) !== 'string');

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
