import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { ORGANIZATION_USER_ENTITY_NAME, createOrganizationUserDescriptor } from './organization-user.descriptors';
import { ORGANIZATION_USER_I18N_SCOPE } from '../org-admin.i18n';

/** Flattened out of the nested flexbox containers; base-entity does not export its own flattener. */
function leaves(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? leaves(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

function attributesOf() {
  return new Map(leaves(createOrganizationUserDescriptor().attrDescriptors).map((attr) => [attr.attrName, attr]));
}

describe('createOrganizationUserDescriptor', () => {
  it('is named so that snakeCaseName produces the route segment `organization-user`', () => {
    // BaseFormNavigatorSingletonStore builds the details URL from the entity name; a mismatch with
    // ORG_ADMIN_ROUTES stops the username column linking and Edit navigating, with no error anywhere.
    expect(ORGANIZATION_USER_ENTITY_NAME).toBe('Organization User');
  });

  it('resolves its labels from the registered org_admin scope', () => {
    // The derived default would be `organization-user`, which has no `org_admin.` prefix and would
    // miss every key in the bundle this library ships.
    expect(createOrganizationUserDescriptor().scopeRoot()).toBe(ORGANIZATION_USER_I18N_SCOPE);
  });

  it('identifies a user by username in the status bar, and leaves entityTitle empty', () => {
    // The username is the one field that is both unique and stable. A non-empty entityTitle would
    // beat it and show the words "Organization User" where the selected person belongs.
    expect(createOrganizationUserDescriptor().titleKey).toBe('username');
    expect(createOrganizationUserDescriptor().entityTitle).toBe('');
  });

  it('never lets a form write the username, the roles or the verification flag', () => {
    const attributes = attributesOf();

    // Immutable: it is what audit records name.
    expect(attributes.get('username')?.disabled).toBe(true);
    // Roles have their own endpoint, so a profile edit cannot rewrite permissions.
    expect(attributes.get('roleNames')?.disabled).toBe(true);
    // Set by the identity provider when the invitee confirms their address.
    expect(attributes.get('emailVerified')?.disabled).toBe(true);
  });

  it('keeps `enabled` editable, because it is the reversible way to remove someone', () => {
    // Disabling keeps the account, its groups and its roles while making a token unobtainable, so the
    // platform's createdBy and assignee references keep resolving. Deleting leaves them dangling.
    expect(attributesOf().get('enabled')?.disabled).toBe(false);
  });

  it('requires a username and an e-mail address', () => {
    const attributes = attributesOf();

    expect(attributes.get('username')?.required).toBe(true);
    // Required because the invitation and the password reset are sent to it.
    expect(attributes.get('email')?.required).toBe(true);
    expect(attributes.get('firstName')?.required).toBeFalsy();
  });
});
