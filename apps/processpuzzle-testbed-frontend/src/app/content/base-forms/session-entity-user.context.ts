import { effect, inject, Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { EntityUserContext } from '@processpuzzle/base-entity';

/**
 * Binds base-entity's session seam to this application's real session, so a comment is stamped with the name
 * of whoever wrote it. base-entity cannot read the session itself — auth depends on base-entity — which is why
 * the host closes the loop here, the way {@link SessionUserContext} does for base-workflow.
 *
 * `AUTHENTICATION_SERVICE` is optional for the same reason as there: without an `AUTHENTICATION_PROVIDER` its
 * factory throws, and an auth-less deployment should get comments by an unknown author, not an injector error.
 */
@Injectable()
export class SessionEntityUserContext extends EntityUserContext {
  private readonly authenticationService = inject(AUTHENTICATION_SERVICE, { optional: true });

  constructor() {
    super();
    // An effect: the user arrives asynchronously, after the OIDC redirect is finalized, and a refresh can replace it.
    effect(() => {
      const user = this.authenticationService?.currentUser();
      const fullName = [user?.firstName, user?.lastName].filter((part) => !!part?.trim()).join(' ');
      this.set({ userId: user?.id ?? '', displayName: fullName || user?.email || '' });
    });
  }
}
