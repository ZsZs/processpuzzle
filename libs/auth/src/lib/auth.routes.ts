import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { MyProfileComponent } from './my-profile/my-profile.component';
import { RegistrationComponent } from './registration/registration.component';
import { LogoutComponent } from './logout/logout.component';
import { provideTranslocoScope } from '@jsverse/transloco';

const loader = ['de', 'en', 'es', 'fr', 'hu'].reduce((acc: any, lang) => {
  acc[lang] = () => import(`./assets/i18n/auth/${lang}.json`);
  return acc;
}, {});

export const authRoutes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, title: 'login', data: { icon: 'login', authToggle: true }, providers: [provideTranslocoScope({ scope: 'auth', loader })] },
  { path: 'logout', component: LogoutComponent, title: 'logout', data: { icon: 'logout', authToggle: false } },
  { path: 'register', component: RegistrationComponent, title: 'register', data: { icon: 'person_add', authToggle: true } },
  { path: 'my-profile', component: MyProfileComponent, title: 'my_profile', data: { icon: 'person', authToggle: false } },
];
