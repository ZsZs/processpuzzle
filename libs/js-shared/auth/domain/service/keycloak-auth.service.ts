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
   * router URL such as "/home" (leading slash) or undefined.
   */
  private toRedirectUri(target?: string): string {
    const relative = (target ?? '').replace(/^\/+/, ''); // strip leading slash(es)
    return new URL(relative, document.baseURI).href;
  }
  // endregion
}
