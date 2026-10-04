import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import de from '../../assets/i18n/base_app/de.json';
import en from '../../assets/i18n/base_app/en.json';
import es from '../../assets/i18n/base_app/es.json';
import fr from '../../assets/i18n/base_app/fr.json';
import hu from '../../assets/i18n/base_app/hu.json';
import { createAppDefinitionDescriptor } from './app-definition.descriptors';
import { createModuleDefinitionDescriptor } from './module-definition.descriptors';
import { createModuleMountDescriptor } from './module-mount.descriptors';
import { createNavItemDescriptor } from './nav-item.descriptors';
import { createRegionDefinitionDescriptor } from './region-definition.descriptors';
import { createRouteDefinitionDescriptor } from './route-definition.descriptors';
import { createWidgetInstanceDescriptor } from './widget-instance.descriptors';

/**
 * Every field of the designer's forms carries a tooltip, in every language the library ships — the tooltips
 * are what point the person assembling an application at the matching section of the concepts guide.
 */
describe('base-app form tooltips', () => {
  const descriptors: BaseEntityDescriptor[] = [
    createAppDefinitionDescriptor(),
    createRegionDefinitionDescriptor(),
    createRouteDefinitionDescriptor(),
    createModuleMountDescriptor(),
    createNavItemDescriptor(),
    createWidgetInstanceDescriptor(),
    createModuleDefinitionDescriptor(),
  ];
  const keys = descriptors.flatMap((descriptor) => attributesOf(descriptor.attrDescriptors).map((attr) => attr.tooltipI18nKey()));

  it.each(Object.entries({ en, de, es, fr, hu }))('has a tooltip for every form field in %s', (_lang, translation) => {
    const missing = keys.filter((key) => typeof resolve({ base_app: translation }, key) !== 'string');

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
