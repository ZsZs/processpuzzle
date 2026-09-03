import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { ORGANIZATION_ENTITY_NAME, createOrganizationDescriptor } from './organization.descriptors';
import { ORGANIZATION_I18N_SCOPE } from '../platform-admin.i18n';

/**
 * Attribute descriptors by name, flattened out of the nested flexbox containers.
 *
 * Recursed here rather than through base-entity's own `filterAttributeDescriptors`, which the library
 * does not export.
 */
function leaves(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? leaves(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

function attributesOf(descriptor = createOrganizationDescriptor()) {
  return new Map(leaves(descriptor.attrDescriptors).map((attr) => [attr.attrName, attr]));
}

describe('createOrganizationDescriptor', () => {
  it('is named so that the route segment snakeCaseName produces is `organization`', () => {
    // BaseFormNavigatorSingletonStore builds the details URL from the entity name. A mismatch with
    // PLATFORM_ADMIN_ROUTES does not fail loudly: the Name column stops linking and Edit goes nowhere.
    expect(ORGANIZATION_ENTITY_NAME).toBe('Organization');
    expect(createOrganizationDescriptor().entityName).toBe(ORGANIZATION_ENTITY_NAME);
  });

  it('resolves its labels from the registered platform_admin scope', () => {
    // The derived default would be `organization`, which has no `platform_admin.` prefix and so
    // would miss every key in the bundle this library ships.
    expect(createOrganizationDescriptor().scopeRoot()).toBe(ORGANIZATION_I18N_SCOPE);
    expect(createOrganizationDescriptor().i18nKey()).toBe(`${ORGANIZATION_I18N_SCOPE}._self`);
  });

  it('identifies a tenant in the status bar by its key, not its name', () => {
    // Two customers can both be called "Acme"; the key is what an operator was given.
    expect(createOrganizationDescriptor().titleKey).toBe('key');
  });

  it('leaves entityTitle empty, because a non-empty one would shadow the selected row', () => {
    expect(createOrganizationDescriptor().entityTitle).toBe('');
  });

  it('never lets a form write the key or the status', () => {
    const attributes = attributesOf();

    // The key is the tenant's public URL and the scope of all its metadata: renaming it orphans
    // every id, which is why OrganizationUpdate has no field for it.
    expect(attributes.get('key')?.disabled).toBe(true);
    // The status moves only through suspend and activate, each of which has a realm call to make.
    expect(attributes.get('status')?.disabled).toBe(true);
  });

  it('requires a name and leaves the rest optional', () => {
    const attributes = attributesOf();

    expect(attributes.get('name')?.required).toBe(true);
    expect(attributes.get('contactEmail')?.required).toBeFalsy();
    expect(attributes.get('description')?.required).toBeFalsy();
  });

  it('declares every field the contract carries, so nothing is silently dropped on save', () => {
    expect([...attributesOf().keys()]).toEqual(
      expect.arrayContaining(['key', 'name', 'description', 'contactEmail', 'defaultLocale', 'status', 'createdAt', 'updatedAt']),
    );
  });
});
