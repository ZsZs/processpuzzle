import { inject } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { AUTHENTICATION_SERVICE, User } from '@processpuzzle/auth/domain';
import { NavigateBackService } from '@processpuzzle/util';

export const loginResolver: ResolveFn<User | undefined> = async () => {
  const authService = inject(AUTHENTICATION_SERVICE);
  const navigateBackService = inject(NavigateBackService);

  return await authService.login(navigateBackService.getRouteStack().pop());
};
