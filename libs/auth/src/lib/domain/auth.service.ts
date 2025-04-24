import { computed, inject, Injectable, Signal } from '@angular/core';
import { Auth, authState, User } from '@angular/fire/auth';
import { toSignal } from '@angular/core/rxjs-interop';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly auth: Auth = inject(Auth);
  private readonly userSignal = toSignal<User | null>(authState(this.auth), { initialValue: null });
  isAuthenticated: Signal<boolean> = computed(() => !!this.userSignal());

  async signOut(): Promise<void> {
    return this.auth.signOut();
  }

  getCurrentUser(): User | null {
    return this.auth.currentUser;
  }
}
