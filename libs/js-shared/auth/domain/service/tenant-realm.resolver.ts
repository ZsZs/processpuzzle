/**
 * Resolves a tenant's Keycloak realm from the URL, before Angular bootstraps.
 *
 * Realm-per-organization means the SPA cannot be built with its realm baked in: `processpuzzle-ui`
 * serves every tenant from one bundle, and which realm to authenticate against is only knowable from
 * the URL the user arrived at. `KeycloakAuthService` constructs its `Keycloak` instance eagerly from
 * `AUTH_SERVICE_CONFIG.realm`, so the substitution has to happen before `bootstrapApplication` — see
 * `resolveTenantRealm`.
 *
 * The alternative, making the auth service lazy, was rejected: every consumer of
 * `AUTHENTICATION_SERVICE` would have to become async, and the app initializer that restores an SSO
 * session would have nothing to initialize yet.
 */

/** The first path segment of a URL path, or `undefined` when there is none. */
export function firstPathSegment(pathname: string): string | undefined {
  const segment = pathname.split('/').find((part) => part.length > 0);
  return segment === undefined ? undefined : decodeURIComponent(segment);
}

/**
 * Whether `segment` could be an organization key.
 *
 * The same slug rule the backend enforces (`^[a-z0-9]+(-[a-z0-9]+)*$`, 2–63 characters), checked by
 * scanning rather than by that regex: the nested repetition makes the JDK engine recurse per
 * character and JavaScript engines backtrack, so a long adversarial segment is a hazard for no
 * benefit. `CheckOrganizationKey` on the backend scans for the same reason.
 */
export function looksLikeOrgKey(segment: string | undefined): boolean {
  if (!segment || segment.length < 2 || segment.length > 63) return false;
  if (segment.startsWith('-') || segment.endsWith('-')) return false;
  let previousWasHyphen = false;
  for (const character of segment) {
    if (character === '-') {
      if (previousWasHyphen) return false;
      previousWasHyphen = true;
      continue;
    }
    if (!/[a-z0-9]/.test(character)) return false;
    previousWasHyphen = false;
  }
  return true;
}

/**
 * The realm to authenticate against for the current URL.
 *
 * @param realmTemplate a realm name containing `{orgKey}`, or a fixed realm name with no placeholder
 * @param pathname the URL path to read the tenant from; defaults to the current location
 * @param fallbackRealm used when the path names no plausible tenant — a landing page, the sign-up
 *   form, or a deep link that starts with a reserved segment such as `login`
 * @returns the substituted realm, or `fallbackRealm` when no tenant could be read
 *
 * Reserved segments are deliberately **not** filtered here. `ReservedOrganizationKeys` lives on the
 * backend and this helper runs before the first HTTP call, so it cannot consult it; a URL beginning
 * with a reserved segment simply resolves to a realm that does not exist, and Keycloak's own
 * "realm does not exist" is a clearer answer than a guess made here would be. What this does filter
 * is anything that could not be an organization key at all, so `/login` does not become a realm
 * lookup for `login`.
 */
export function resolveTenantRealm(realmTemplate: string, pathname?: string, fallbackRealm?: string): string {
  if (!realmTemplate.includes('{orgKey}')) return realmTemplate;

  const path = pathname ?? (typeof window === 'undefined' ? '/' : window.location.pathname);
  const segment = firstPathSegment(path);
  if (!looksLikeOrgKey(segment)) return fallbackRealm ?? realmTemplate.replace('{orgKey}', '');

  return realmTemplate.replace('{orgKey}', segment as string);
}

/** The tenant key the current URL names, or `undefined` — the same reading {@link resolveTenantRealm} does. */
export function currentOrgKey(pathname?: string): string | undefined {
  const path = pathname ?? (typeof window === 'undefined' ? '/' : window.location.pathname);
  const segment = firstPathSegment(path);
  return looksLikeOrgKey(segment) ? segment : undefined;
}
