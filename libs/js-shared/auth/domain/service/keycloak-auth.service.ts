import { AuthService } from './auth.service';
import { User } from '../user/user';
import Keycloak from 'keycloak-js';
import { KeycloakAuthConfig } from './keycloak-auth.config';

export class KeycloakAuthService extends AuthService {
  readonly keycloak: Keycloak;
  private initPromise: Promise<void> | null = null;

  constructor(protected config: KeycloakAuthConfig) {
    super();
    this.keycloak = new Keycloak({
      clientId: config.clientId,
      realm: config.realm,
      url: config.authServerUrl,
    });
  }

  // region public accessors and mutators
  override async authenticate(): Promise<boolean> {
    await this.ensureInitialized();
    return !!this.getCurrentUser();
  }

  async login(redirectUrl?: string): Promise<User> {
    await this.ensureInitialized();
    if (this.isAuthenticated()) return this.user() as User;
    else {
      await this.keycloak.login({ redirectUri: this.toRedirectUri(redirectUrl) });
      return this.getCurrentUser() as User;
    }
  }

  override async logout(redirectUrl?: string): Promise<void> {
    await this.keycloak.logout({ redirectUri: this.toRedirectUri(redirectUrl) });
    this._user.set(undefined);
  }

  override getCurrentUser(): User | undefined {
    return this.user();
  }

  getUsername(): string {
    return this.keycloak.profile?.username || '';
  }

  getUserRoles(): string[] {
    return this.keycloak.realmAccess?.roles || [];
  }

  /**
   * The raw access token, for the `Authorization: Bearer` header.
   *
   * Read from the live Keycloak instance on every call rather than cached: keycloak-js refreshes the
   * token in the background, and a cached copy would start failing with 401 partway through a session
   * with nothing to indicate why.
   */
  override getAccessToken(): string | undefined {
    return this.keycloak.token;
  }

  /**
   * Refreshes the token when it expires within `minValiditySeconds`, so a long-running screen does
   * not send one that dies in flight. Resolves to false when there is nothing to refresh.
   */
  override async refreshAccessToken(minValiditySeconds = 30): Promise<boolean> {
    if (!this.keycloak.authenticated) return false;
    try {
      return await this.keycloak.updateToken(minValiditySeconds);
    } catch {
      // A failed refresh means the session is gone. Surfacing it as false lets the interceptor send
      // the request unauthenticated and let the server answer 401, which is a clearer signal to the
      // user than an exception from an HTTP interceptor.
      return false;
    }
  }

  /**
   * Roles are read from the token once, at init, so a role granted afterwards stays invisible until the
   * token is replaced. `updateToken(-1)` forces that replacement; the user is then rebuilt from the new
   * token with the profile it already had. A failed refresh leaves the user as it was.
   */
  override async reloadUser(): Promise<void> {
    const current = this.user();
    if (!current || !this.keycloak.authenticated) return;
    try {
      await this.keycloak.updateToken(-1);
    } catch {
      return;
    }
    this._user.set(new User(current.email, current.id, current.firstName, current.lastName, current.photoUrl, this.getUserRoles()));
  }

  /**
   * Keycloak's account console. `returnUrl` is passed through as is, unlike {@link login}'s target: the
   * profile page that links here lives under `/auth`, which {@link toRedirectUri} would send home.
   */
  override getAccountManagementUrl(returnUrl?: string): string | undefined {
    try {
      return this.keycloak.createAccountUrl(returnUrl ? { redirectUri: returnUrl } : undefined);
    } catch {
      // Not initialized yet, so the realm URL is unknown. The page shows no link rather than a broken one.
      return undefined;
    }
  }
  // endregion

  // region protected, private helper methods
  private ensureInitialized(): Promise<void> {
    this.initPromise ??= this.initKeycloak();
    return this.initPromise;
  }

  private async initKeycloak() {
    await this.keycloak.init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: new URL('assets/auth/silent-check-sso.html', document.baseURI).href,
      // The default for login() too, so the silent check and an interactive login ask for the same
      // claims. Omitted when not configured: an explicit undefined would still be one more key.
      ...(this.config.scope ? { scope: this.config.scope } : {}),
    });

    // Check if authenticated using the keycloak-js instance directly
    if (this.keycloak.authenticated) {
      const profile = await this.keycloak.loadUserProfile();

      // The roles come along now. They were readable through getUserRoles() all along but never
      // reached the User, so nothing outside this class could ask what the signed-in person may do
      // without knowing which AuthService implementation it had.
      const user = new User(profile.email || '', profile.id || '', profile.firstName || '', profile.lastName || '', null, this.getUserRoles());

      this._user.set(user);
    } else {
      this._user.set(undefined);
    }
  }

  /**
   * Builds an absolute redirect URI that honours the app's <base href>
   * (e.g. "/cmdb/frontend/"), not just the origin. `target` is an Angular
   * router URL such as "/home" (leading slash) or undefined. The auth pages themselves
   * ("/en/auth/register", "/auth/login", ...) are never a destination: they fall back to the home page.
   */
  private toRedirectUri(target?: string): string {
    const stripped = (target ?? '').replace(/^\/+/, ''); // strip leading slash(es)
    const relative = /^([a-z]{2}\/)?auth(\/|\?|#|$)/i.test(stripped) ? '' : stripped;
    return new URL(relative, document.baseURI).href;
  }
  // endregion
}
