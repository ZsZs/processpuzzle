import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

/** What the application knows about a realm role beyond its id: a display name and what it is for. */
export interface RoleDescription {
  name?: string;
  description?: string;
}

/**
 * Port through which the application tells auth what its roles mean.
 *
 * A token carries role ids and nothing else, and the ids a tenant's business definitions mint are
 * described where they are authored — base-workflow's role catalog — which auth may not depend on.
 * The application closes the seam by binding an adapter here; without one, roles are shown by id.
 */
export interface RoleDescriptionSource {
  /** Descriptions keyed by realm role id. Roles missing from the map are simply undescribed. */
  describeRoles(): Observable<ReadonlyMap<string, RoleDescription>>;
}

export const ROLE_DESCRIPTION_SOURCE = new InjectionToken<RoleDescriptionSource>('ROLE_DESCRIPTION_SOURCE');

/**
 * Realm roles Keycloak creates with every realm and grants every user through
 * `default-roles-<realm>`. They describe token mechanics, not what the user may do in the
 * application, so they are not shown to the user. The backend reserves the same names in
 * `SyncRoleDirectory`.
 */
const IDENTITY_PROVIDER_ROLES: ReadonlySet<string> = new Set(['offline_access', 'uma_authorization']);
const DEFAULT_ROLES_PREFIX = 'default-roles-';

/** Whether `role` is one of the identity provider's own roles rather than one the platform or a tenant defines. */
export function isIdentityProviderRole(role: string): boolean {
  return IDENTITY_PROVIDER_ROLES.has(role) || role.startsWith(DEFAULT_ROLES_PREFIX);
}
