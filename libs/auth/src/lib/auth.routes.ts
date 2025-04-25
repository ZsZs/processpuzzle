import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { MyProfileComponent } from './my-profile/my-profile.component';
import { RegistrationComponent } from './registration/registration.component';
import { LogoutComponent } from './logout/logout.component';

export const authRoutes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, title: 'Login', data: { icon: 'login', authToggle: true } },
  { path: 'logout', component: LogoutComponent, title: 'Logout', data: { icon: 'logout', authToggle: false } },
  { path: 'register', component: RegistrationComponent, title: 'Register', data: { icon: 'person_add', authToggle: true } },
  { path: 'my-profile', component: MyProfileComponent, title: 'My profile', data: { icon: 'person', authToggle: false } },
];
