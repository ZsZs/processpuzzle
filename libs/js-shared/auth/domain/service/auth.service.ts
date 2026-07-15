import { computed, Injectable, signal, Signal, WritableSignal } from '@angular/core';
import { User } from '../user/user';

@Injectable({
  providedIn: 'root',
})
export abstract class AuthService {
  protected _user: WritableSignal<User | undefined> = signal<User | undefined>(undefined);
  protected readonly user: Signal<User | undefined> = this._user.asReadonly();
  readonly currentUser: Signal<User | undefined> = this._user.asReadonly();
  isAuthenticated: Signal<boolean | undefined> = computed(() => (this.user ? !!this.user() : undefined));

  abstract authenticate(): Promise<boolean>;

  abstract login(redirectUrl?: string, email?: string, password?: string): Promise<User | undefined>;

  abstract logout(redirectUrl?: string): Promise<void>;

  abstract getCurrentUser(): User | undefined;

  // region protected, private helper methods
  protected throwError(message: string) {
    throw new Error(message);
  }
  // endregion
}
