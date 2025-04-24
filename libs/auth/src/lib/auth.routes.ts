import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { PersonalDataComponent } from './personal-data/personal-data.component';
import { RegistrationComponent } from './registration/registration.component';
import { LogoutComponent } from './logout/logout.component';

export const authRoutes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, title: 'Login' },
  { path: 'logout', component: LogoutComponent, title: 'Logout' },
  { path: 'register', component: RegistrationComponent, title: 'Registration' },
  { path: 'personal-data', component: PersonalDataComponent, title: 'Personal Data' },
];
