import { inject } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { AUTHENTICATION_CONFIGURATION, AUTHENTICATION_SERVICE, AuthenticationConfiguration, User } from '@processpuzzle/auth/domain';
import { NavigateBackService } from '@processpuzzle/widgets';

export const loginResolver: ResolveFn<User | undefined> = async () => {
  const authConfig: AuthenticationConfiguration = inject(AUTHENTICATION_CONFIGURATION);
  const authService = inject(AUTHENTICATION_SERVICE);
  const navigateBackService = inject(NavigateBackService);

  if (authConfig.AUTHENTICATION_PROVIDER === 'firebase-auth') return undefined;
  else return await authService.login(navigateBackService.getRouteStack().pop());
};
