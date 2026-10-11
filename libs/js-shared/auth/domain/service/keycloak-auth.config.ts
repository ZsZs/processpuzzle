export interface KeycloakAuthConfig {
  realm: string;
  clientId: string;
  authServerUrl: string;
  /**
   * The `scope` parameter every login and silent SSO check sends, space-delimited; keycloak-js adds
   * `openid` itself. Absent means only the client's default scopes. A Keycloak Organization scope
   * is the reason it exists: `organization:<alias>` or `organization:*` choose which memberships the
   * token's `organization` claim names, where the default `organization` scope names one at most.
   */
  scope?: string;
}
