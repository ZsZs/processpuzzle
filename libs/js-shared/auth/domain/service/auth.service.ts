import { computed, Injectable, signal, Signal, WritableSignal } from '@angular/core';
import { User } from '../user/user';

@Injectable({
  providedIn: 'root',
})
export abstract class AuthService {
  protected _user: WritableSignal<User | undefined> = signal<User | undefined>(undefined);
  protected readonly user: Signal<User | undefined> = this._user.asReadonly();
  readonly currentUser: Signal<User | undefined> = this._user.asReadonly();
  isAuthenticated: Signal<boolean | undefined> = computed(() => (this.user ? !!this.user() : undefined));

  abstract authenticate(): Promise<boolean>;

  abstract login(redirectUrl?: string, email?: string, password?: string): Promise<User | undefined>;

  abstract logout(redirectUrl?: string): Promise<void>;

  abstract getCurrentUser(): User | undefined;

  /**
   * The bearer token to attach to API calls, or `undefined` when this provider has none.
   *
   * Defaults to `undefined` rather than being abstract so that an implementation which does not
   * issue bearer tokens — the local and Firebase ones — needs no change, and
   * {@link authTokenInterceptor} simply sends the request unauthenticated. Making it abstract would
   * have forced every implementation to declare that it has no token.
   */
  getAccessToken(): string | undefined {
    return undefined;
  }

  /**
   * Refreshes the token if it is about to expire. Returns whether a refresh happened.
   *
   * The default does nothing and reports false, for the same reason as {@link getAccessToken}.
   */
  async refreshAccessToken(_minValiditySeconds?: number): Promise<boolean> {
    return false;
  }

  // region protected, private helper methods
  protected throwError(message: string) {
    throw new Error(message);
  }
  // endregion
}
