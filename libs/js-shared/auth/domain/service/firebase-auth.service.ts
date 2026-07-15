import { AuthService } from './auth.service';
import { Auth, signInWithEmailAndPassword } from '@angular/fire/auth';
import { User } from '../user/user';

export class FirebaseAuthService extends AuthService {
  private readonly auth: Auth;

  constructor(auth: Auth) {
    super();
    this.auth = auth;
  }

  // region public accessors and mutator methods
  override authenticate(): Promise<boolean> {
    return Promise.resolve(this.auth.currentUser !== null);
  }

  public async login(redirectUrl?: string, email?: string, password?: string): Promise<User | undefined> {
    if (email != null && password != null) {
      const userCredential = await signInWithEmailAndPassword(this.auth, email, password);
      const user = new User(userCredential.user.email, userCredential.user.uid, userCredential.user.displayName, null, userCredential.user.photoURL);
      this._user.set(user);
      return user;
    } else return undefined;
  }

  override logout(): Promise<void> {
    return this.auth.signOut();
  }

  getCurrentUser(): User | undefined {
    const currentUser = this.auth.currentUser;
    return new User(currentUser?.email, currentUser?.uid, currentUser?.displayName, null, currentUser?.photoURL);
  }
  // endregion
}
