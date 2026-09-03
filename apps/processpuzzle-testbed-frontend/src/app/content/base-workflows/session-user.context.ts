import { effect, inject, Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth';
import { CurrentUserContext } from '@processpuzzle/base-workflow';

/**
 * Binds base-workflow's session seam to this application's real session.
 *
 * `CurrentUserContext` is a seam on purpose: base-workflow's task dashboard needs to know who is looking
 * at it, and `@processpuzzle/auth` is not one of that library's dependencies — a task list is not a reason
 * for a feature library to depend on the authentication one. So the host closes the loop, which is this
 * class, provided over the default in `app.config.ts`.
 *
 * `AUTHENTICATION_SERVICE` is injected **optionally**: its factory throws when no
 * `AUTHENTICATION_PROVIDER` is configured, and a misconfigured or auth-less deployment should get a
 * dashboard with an empty inbox rather than an injector error on a screen that has nothing to do with
 * signing in.
 *
 * **Roles are left empty, and that is a real gap rather than an oversight.** `User` carries no roles —
 * neither the Firebase nor the Keycloak implementation projects claims into it — so there is nothing here
 * to forward. The consequence is documented on `CurrentUserContext`: the Team queue offers every claimable
 * task instead of filtering by role (the backend still refuses a claim by a user without the role), and the
 * Skip override stays hidden, because an unstated role is not a granted one. Wiring real roles means
 * surfacing them on `User` first, in the auth library.
 */
@Injectable()
export class SessionUserContext extends CurrentUserContext {
  private readonly authenticationService = inject(AUTHENTICATION_SERVICE, { optional: true });

  constructor() {
    super();
    // An effect rather than a one-off read: the user arrives asynchronously — the OIDC redirect is
    // finalized in an app initializer and a token refresh can replace it later — so a value read in the
    // constructor would be `undefined` for the lifetime of the application.
    effect(() => {
      const user = this.authenticationService?.currentUser();
      this.set({ userId: user?.id ?? '', roles: [] });
    });
  }
}
