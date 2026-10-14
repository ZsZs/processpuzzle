import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Presentational sign-in form for Keycloak's `login.ftl`, shipped as the `<pp-login-form>` custom
 * element (apps/login-element). It holds no credentials logic: it renders a plain HTML form whose
 * `action` is Keycloak's `url.loginAction`, so CSRF, session handling and the Authorization Code +
 * PKCE flow stay exactly as Keycloak runs them. Every text arrives as an attribute because the
 * theme owns localisation (`msg(...)`).
 */
@Component({
  selector: 'pp-login-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: 'login-form.component.html',
  styleUrl: 'login-form.component.css',
})
export class LoginFormComponent {
  readonly action = input.required<string>();
  readonly error = input<string>('');
  readonly forgotPasswordLabel = input<string>('');
  readonly forgotPasswordUrl = input<string>('');
  readonly passwordLabel = input<string>('Password');
  readonly registerLabel = input<string>('');
  readonly registerUrl = input<string>('');
  readonly submitLabel = input<string>('Sign in');
  readonly heading = input<string>('Sign in');
  readonly username = input<string>('');
  readonly usernameLabel = input<string>('Email');
}
