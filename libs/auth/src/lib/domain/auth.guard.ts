import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard = async () => {
  const router = inject(Router);
  const authService = inject(AuthService);

  if (authService.user()) return true;
  else {
    await router.navigate(['/auth/login']);
    return false;
  }
};
