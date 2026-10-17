import { Injectable, signal } from '@angular/core';

/**
 * The signed-in user's basic data — who it is and the name people know them by — for every library that has
 * to know, without depending on `@processpuzzle/auth`.
 *
 * Here because util is the one library every feature depends on and auth depends on too: auth fills it on
 * sign-in, token refresh and sign-out (`provideAuthenticationService`), and base-entity's `EntityUserContext`
 * and base-workflow's `CurrentUserContext` read it by default. A host therefore no longer wires an adapter per
 * library; it still may override either seam.
 *
 * **No roles.** What the identity provider calls a role — a Keycloak realm role — is not what a feature
 * means by one, and treating the two as the same would change who sees what. The feature seams keep their own.
 *
 * Empty when nobody is signed in, and in an application without authentication.
 */
@Injectable({ providedIn: 'root' })
export class SessionUser {
  private readonly _userId = signal('');
  private readonly _displayName = signal('');

  readonly userId = this._userId.asReadonly();
  /** Full name where the identity provider knows one, the email otherwise; empty when it knows neither. */
  readonly displayName = this._displayName.asReadonly();

  set(user: { userId: string; displayName?: string }): void {
    this._userId.set(user.userId);
    this._displayName.set(user.displayName ?? '');
  }

  clear(): void {
    this.set({ userId: '' });
  }
}
