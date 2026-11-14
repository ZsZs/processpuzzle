import { Directive, inject } from '@angular/core';
import { AuthButtonComponent } from './auth-button.component';

@Directive({
  selector: '[appRemoveRegistrationRoute]',
  standalone: true,
})
export class RemoveRegistrationRouteDirective {
  constructor() {
    const authButton = inject(AuthButtonComponent);
    const routes = authButton.routes;
    routes.splice(0, routes.length, ...routes.filter((route) => route.path !== 'register'));
  }
}
