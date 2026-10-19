import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import de from '../../assets/i18n/base_calculation/de.json';
import en from '../../assets/i18n/base_calculation/en.json';
import es from '../../assets/i18n/base_calculation/es.json';
import fr from '../../assets/i18n/base_calculation/fr.json';
import hu from '../../assets/i18n/base_calculation/hu.json';
import { CALCULATION_DEFINITION_ENTITY_NAME, createCalculationDefinitionDescriptor } from './calculation-definition.descriptors';

function attributesOf(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? attributesOf(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

function resolve(translation: object, key: string | undefined): unknown {
  return key?.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown> | undefined)?.[segment], translation);
}

describe('createCalculationDefinitionDescriptor', () => {
  const descriptor = createCalculationDefinitionDescriptor();
  const attributes = attributesOf(descriptor.attrDescriptors);

  it('describes the calculation definition under its scope', () => {
    expect(descriptor.entityName).toBe(CALCULATION_DEFINITION_ENTITY_NAME);
    expect(descriptor.i18nKey()).toBe('base_calculation.calculation_definition._self');
  });

  it('edits the source only; the rest is derived from it', () => {
    expect(attributes.filter((attr) => !attr.disabled).map((attr) => attr.attrName)).toEqual(['source']);
    expect(attributes.find((attr) => attr.attrName === 'id')?.isLinkToDetails).toBe(true);
  });

  it.each(Object.entries({ en, de, es, fr, hu }))('has a label and a tooltip for every field in %s', (_lang, translation) => {
    const keys = attributes.flatMap((attr) => [attr.i18nKey(), attr.tooltipI18nKey()]);

    expect(keys.filter((key) => typeof resolve({ base_calculation: translation }, key) !== 'string')).toEqual([]);
  });
});
