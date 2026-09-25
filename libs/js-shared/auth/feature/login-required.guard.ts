import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';

/**
 * Lets an authenticated user through, and sends anybody else to log in and back to the URL they asked for.
 *
 * **Not {@link authGuard}, which sends to `/auth/login` instead.** That route's resolver returns to
 * the top of `NavigateBackService`'s history — and on a page opened cold, from a bookmark or from a
 * link in an e-mail, the history is empty, so the user comes back from the identity provider to `/`
 * rather than to the page they were refused. For a tenant application that is not merely a detour:
 * `/` names no organization, so the user lands on a page that cannot tell who they are.
 *
 * **The requested URL goes to the provider directly.** With Keycloak, `login()` redirects the whole
 * document and never resolves. A provider without a hosted login page — Firebase — returns without
 * a session instead, and gets the application's own form.
 */
export const loginRequiredGuard: CanActivateFn = async (_route, state) => {
  const authService = inject(AUTHENTICATION_SERVICE);
  const router = inject(Router);

  if (await authService.authenticate()) return true;

  await authService.login(state.url);
  return authService.isAuthenticated() ? true : router.createUrlTree(['/auth/login']);
};
