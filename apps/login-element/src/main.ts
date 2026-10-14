import { createApplication } from '@angular/platform-browser';
import { createCustomElement } from '@angular/elements';
import { provideBrowserGlobalErrorListeners } from '@angular/core';
import { LoginFormComponent } from '@processpuzzle/auth/login-form';

// Only the custom element is registered; there is no root component to bootstrap. Keycloak's
// login.ftl loads this bundle and places <pp-login-form> in the page.
createApplication({ providers: [provideBrowserGlobalErrorListeners()] })
  .then((app) => customElements.define('pp-login-form', createCustomElement(LoginFormComponent, { injector: app.injector })))
  .catch((err) => console.error(err));
