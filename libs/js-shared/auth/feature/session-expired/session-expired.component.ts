import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';

/**
 * What the user sees when their session has ended and nothing could silently restore it: an explanation, a
 * "Sign in again" button and a way back to the start page. Reached from `sessionExpiredInterceptor` (when its
 * redirect loop guard stops a second login) and from guards whose read was refused with 401, so a dead session is
 * never shown as a blank page or as an application that looks alive and fails on every call.
 *
 * The page the user was on arrives as the `returnUrl` query parameter; signing in again comes back to it.
 */
@Component({
  selector: 'pp-session-expired',
  imports: [MatButton, MatIcon, TranslocoDirective],
  providers: [provideTranslocoScope('auth')],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="session-expired" role="alert" *transloco="let t">
      <mat-icon class="session-expired__icon" aria-hidden="true">lock_clock</mat-icon>
      <h2>{{ t('auth.session_expired.title') }}</h2>
      <p>{{ t('auth.session_expired.message') }}</p>
      <div class="session-expired__actions">
        <button mat-button type="button" (click)="goToStartPage()">{{ t('auth.session_expired.home_button') }}</button>
        <button mat-raised-button color="primary" type="button" [disabled]="signingIn()" (click)="signInAgain()">{{ t('auth.session_expired.sign_in_button') }}</button>
      </div>
    </section>
  `,
  styles: [
    `
      .session-expired {
        align-items: center;
        display: flex;
        flex-direction: column;
        gap: 12px;
        margin: 48px auto;
        max-width: 480px;
        padding: 0 16px;
        text-align: center;
      }

      .session-expired__icon {
        font-size: 48px;
        height: 48px;
        width: 48px;
      }

      .session-expired__actions {
        display: flex;
        gap: 12px;
        margin-top: 8px;
      }
    `,
  ],
})
export class SessionExpiredComponent {
  private readonly authService = inject(AUTHENTICATION_SERVICE);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly signingIn = signal(false);

  /** Drops whatever is left of the session, then goes to the identity provider and back to the page the user was on. */
  async signInAgain(): Promise<void> {
    this.signingIn.set(true);
    try {
      // `login` returns early for a user it still believes is signed in.
      this.authService.clearSession();
      const returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
      await this.authService.login(returnUrl);
      // Keycloak replaces the document and never gets here. A provider without a hosted login page does, signed in.
      if (this.authService.isAuthenticated()) await this.router.navigateByUrl(`/${returnUrl}`);
    } finally {
      this.signingIn.set(false);
    }
  }

  async goToStartPage(): Promise<void> {
    await this.router.navigateByUrl('/');
  }
}

/**
 * The `returnUrl` is an in-app router URL, and arrives in a link anybody can craft: anything that names another
 * origin (a scheme, or `//host`) or smuggles a backslash is dropped, and the user comes back to the start page instead.
 */
function safeReturnUrl(candidate: string | null): string {
  if (!candidate) return '';
  const isForeign = /^[a-z][a-z\d+.-]*:/i.test(candidate) || candidate.startsWith('//') || candidate.includes('\\');
  return isForeign ? '' : candidate.replace(/^\/+/, '');
}
