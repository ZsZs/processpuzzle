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
    if (this.isAuthenticated()) return new Promise(this.user);
    else {
      await this.keycloak.login({ redirectUri: window.location.origin + '/' + redirectUrl });
      return this.getCurrentUser() as User;
    }
  }

  override async logout(redirectUrl?: string): Promise<void> {
    await this.keycloak.logout({ redirectUri: window.location.origin + '/' + redirectUrl });
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
  // endregion

  // region protected, private helper methods
  private ensureInitialized(): Promise<void> {
    this.initPromise ??= this.initKeycloak();
    return this.initPromise;
  }

  private async initKeycloak() {
    await this.keycloak.init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: 'http://localhost:4200/assets/auth/silent-check-sso.html',
    });

    console.log('Keycloak initialized');

    // Check if authenticated using the keycloak-js instance directly
    if (this.keycloak.authenticated) {
      const profile = await this.keycloak.loadUserProfile();

      // Map to your User domain model
      const user: User = {
        uid: profile.id || '',
        displayName: `${profile.firstName} ${profile.lastName}`,
        email: profile.email || '',
        // Add other fields as required by your User interface
      } as unknown as User;

      this._user.set(user);
    } else {
      this._user.set(undefined);
    }
  }
  // endregion
}
