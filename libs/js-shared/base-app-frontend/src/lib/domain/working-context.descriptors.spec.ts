import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { APP_DEFINITION_ENTITY_NAME } from './app-entity-names';
import { APP_CONTEXT_ENTITY_NAME, APP_CONTEXT_ID_FIELD, createWorkingContextDescriptor } from './working-context.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createWorkingContextDescriptor', () => {
  const descriptor = createWorkingContextDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is an embedded component of the app definition, identified by its name', () => {
    expect(descriptor.entityName).toBe(APP_CONTEXT_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([APP_DEFINITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
    expect(APP_CONTEXT_ID_FIELD).toBe('name');
  });

  it('describes the identity, the placement and the flattened picker, display and confirmation', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual([
      'name',
      'entityName',
      'parent',
      'basePath',
      'persist',
      'pickerSearchFields',
      'pickerRecent',
      'displayLabel',
      'displayBadge',
      'displayExtra',
      'confirmSwitchStates',
    ]);
  });

  it("requires the name and the entity, the name in the contract's own shape", () => {
    expect(byName('name')?.required).toBe(true);
    expect(byName('name')?.pattern).toBe('^[a-z0-9]+(-[a-z0-9]+)*$');
    expect(byName('entityName')?.required).toBe(true);
  });

  it("offers the contract's persist modes as a dropdown", () => {
    expect(byName('persist')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(
      byName('persist')
        ?.getSelectables()
        ?.map((selectable) => selectable.key),
    ).toEqual(['url', 'url-local-storage']);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.i18nKey()).toBe('base_app.app_context._self');
    expect(byName('displayBadge')?.i18nKey()).toBe('base_app.app_context.displayBadge');
  });
});
