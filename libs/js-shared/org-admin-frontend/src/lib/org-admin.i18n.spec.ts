import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import de from '../assets/i18n/org_admin/de.json';
import en from '../assets/i18n/org_admin/en.json';
import es from '../assets/i18n/org_admin/es.json';
import fr from '../assets/i18n/org_admin/fr.json';
import hu from '../assets/i18n/org_admin/hu.json';
import {
  ORGANIZATION_ROLE_I18N_SCOPE,
  ORGANIZATION_USER_I18N_SCOPE,
  ORG_ADMIN_TRANSLATION_SOURCE,
  ORG_ADMIN_TRANSLOCO_SCOPE,
  ROLE_ASSIGNMENT_I18N_SCOPE,
} from './org-admin.i18n';
import { createOrganizationUserDescriptor } from './domain/organization-user.descriptors';

/** Dotted key paths of a transloco translation file, as transloco flattens them under the scope alias. */
function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('org_admin translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('derives the key roots from the scope the route registers', () => {
    expect(ORG_ADMIN_TRANSLOCO_SCOPE).toBe('org_admin');
    expect(ORGANIZATION_USER_I18N_SCOPE).toBe('org_admin.organization_user');
    expect(ORGANIZATION_ROLE_I18N_SCOPE).toBe('org_admin.organization_role');
  });

  // Roles are a screen *of a user*, so their keys nest under the user rather than under the role
  // entity — the tab label and the save feedback belong to the person whose permissions change.
  it('roots the role-assignment screen under the user, not under the role entity', () => {
    expect(ROLE_ASSIGNMENT_I18N_SCOPE).toBe('org_admin.organization_user.roles');
    expect(ROLE_ASSIGNMENT_I18N_SCOPE.startsWith(`${ORGANIZATION_USER_I18N_SCOPE}.`)).toBe(true);
  });

  it('claims its own scope and names the backend that serves it', () => {
    expect(ORG_ADMIN_TRANSLATION_SOURCE.scopes).toEqual([ORG_ADMIN_TRANSLOCO_SCOPE]);
    expect(ORG_ADMIN_TRANSLATION_SOURCE.serviceRootKey).toBe('ORG_ADMIN_SERVICE_ROOT');
    // Every path in org-admin-api.yaml lives under `/admin`, so a bundle request has to as well.
    expect(ORG_ADMIN_TRANSLATION_SOURCE.segment).toBe('admin');
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });

  it.each(Object.entries(languages))('leaves no key of %s empty', (_language, translations) => {
    const values = flattenKeys(translations).map((key) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], translations));

    expect(values.every((value) => typeof value === 'string' && value.trim().length > 0)).toBe(true);
  });

  it('labels the entity and every attribute of the user form', () => {
    const descriptor = createOrganizationUserDescriptor();
    const scopedKeys = [descriptor.i18nKey(), ...flattenAttrs(descriptor.attrDescriptors).map((attr) => attr.i18nKey())];

    // The descriptor keys carry the scope alias; the file itself starts one level below it.
    const expectedKeys = scopedKeys.map((key) => key?.replace(`${ORG_ADMIN_TRANSLOCO_SCOPE}.`, ''));
    expect(englishKeys).toEqual(expect.arrayContaining(expectedKeys as string[]));
  });

  // Two different labels that both read "Roles" in English and must not be collapsed into one:
  // `roleNames` is the read-only column of the joined string on the user form, while `roles.*` is the
  // separate assignment screen. Merging them would leave the screen's tab, note and feedback unlabelled.
  it('keeps the read-only role column and the role-assignment screen as separate key roots', () => {
    expect(englishKeys).toContain('organization_user.roleNames');
    expect(englishKeys).toEqual(expect.arrayContaining(['organization_user.roles.tab', 'organization_user.roles.save', 'organization_user.roles.note']));
  });
});
