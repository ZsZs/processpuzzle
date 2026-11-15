import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { MyProfileComponent } from './my-profile/my-profile.component';
import { RegistrationComponent } from './registration/registration.component';
import { LogoutComponent } from './logout/logout.component';
import { SessionExpiredComponent } from './session-expired/session-expired.component';
import { authGuard } from './auth.guard';
import { loginRequiredGuard } from './login-required.guard';
import { loginResolver } from './login/login.resolver';
import { provideTranslocoScope } from '@jsverse/transloco';

export const authRoutes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full', providers: [provideTranslocoScope('auth')] },
  { path: 'login', component: LoginComponent, title: 'login', data: { icon: 'login', authToggle: true }, resolve: { signedInUser: loginResolver }, canActivate: [authGuard] },
  { path: 'logout', component: LogoutComponent, title: 'logout', data: { icon: 'logout', authToggle: false }, canActivate: [authGuard] },
  // Open to anybody, signed in or not: it is where a dead session, or a 401 for an application that needs a login, lands.
  // No `title`, deliberately: AuthButtonComponent lists every titled route as a menu entry, and this is not one to choose.
  { path: 'session-expired', component: SessionExpiredComponent },
  { path: 'register', component: RegistrationComponent, title: 'register', data: { icon: 'person_add', authToggle: true } },
  { path: 'my-profile', component: MyProfileComponent, title: 'my_profile', data: { icon: 'person', authToggle: false }, canActivate: [loginRequiredGuard] },
];
