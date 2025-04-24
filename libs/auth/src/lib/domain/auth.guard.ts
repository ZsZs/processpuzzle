import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { map, take } from 'rxjs/operators';

export const authGuard = () => {
  const router = inject(Router);
  const authService = inject(AuthService);

  return authService.user$.pipe(
    take(1),
    map((user) => {
      if (user) return true;

      router.navigate(['/auth/login']);
      return false;
    }),
  );
};
