import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';

export const authGuard = async (route: ActivatedRouteSnapshot) => {
  const authService = inject(AUTHENTICATION_SERVICE);
  const isLoginRoute = route.routeConfig?.path === 'login';
  const isLogoutRoute = route.routeConfig?.path === 'logout';
  const router = inject(Router);
  const snackBar = inject(MatSnackBar);

  await authService.authenticate();

  if (isLoginRoute) {
    if (authService.isAuthenticated()) {
      snackBar.open('You are already logged in', 'Close', { duration: 3000 });
      await router.navigate(['/']);
      return false;
    } else {
      return true; // If not logged in, allow access to login page
    }
  } else if (isLogoutRoute) {
    if (!authService.isAuthenticated()) {
      snackBar.open('You are not already logged in', 'Close', { duration: 3000 });
      await router.navigate(['/']);
      return false;
    } else {
      return true; // If logged in, allow access to logout page
    }
  } else {
    // For protected routes, check if user is logged in
    if (authService.isAuthenticated()) return true;
    else {
      await router.navigate(['/auth/login']);
      return false;
    }
  }
};
