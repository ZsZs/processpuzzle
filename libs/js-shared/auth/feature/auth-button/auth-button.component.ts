import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { authRoutes } from '../auth.routes';
import { SubstringPipe } from '@processpuzzle/util';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';

@Component({
  selector: 'pp-auth-button',
  template: `
    <div class="auth-button">
      <ng-container *transloco="let t">
        <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Auth Button">
          @if (isAuthenticated()) {
            @if (photoUrl()) {
              <img class="avatar" [src]="photoUrl()" alt="User avatar" />
            } @else {
              <span class="avatar avatar--initials" [style.background-color]="avatarColor()">{{ initials() }}</span>
            }
          } @else {
            <mat-icon>person</mat-icon>
          }
        </button>
        <mat-menu #menu="matMenu">
          @for (item of routes; track item) {
            @if ((isAuthenticated() && !item.data?.['authToggle']) || (!isAuthenticated() && item.data?.['authToggle'])) {
              <button mat-menu-item [routerLink]="'auth/' + item.path">
                <mat-icon>{{ item.data?.['icon'] }}</mat-icon>
                <span>&nbsp;{{ t('auth.button.' + item.title | substring: 0) }}</span>
              </button>
            }
          }
        </mat-menu>
      </ng-container>
    </div>
  `,
  imports: [MatIconModule, MatButtonModule, MatMenu, MatMenuItem, RouterLink, MatMenuTrigger, SubstringPipe, TranslocoDirective],
  styles: [
    `
      .auth-button button {
        position: relative;
      }

      .avatar {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 32px;
        height: 32px;
        border-radius: 50%;
        object-fit: cover;
        overflow: hidden;
        font-size: 13px;
        font-weight: 600;
        line-height: 1;
        text-transform: uppercase;
        color: #fff;
        user-select: none;
      }
    `,
  ],
  providers: [provideTranslocoScope('auth')],
})
export class AuthButtonComponent {
  private readonly authService = inject(AUTHENTICATION_SERVICE);
  isAuthenticated = computed(() => this.authService.isAuthenticated());
  readonly routes = authRoutes.filter((item) => item.title !== null && item.title !== undefined);

  private readonly currentUser = computed(() => this.authService.currentUser());
  readonly photoUrl = computed(() => this.currentUser()?.photoUrl || undefined);
  readonly initials = computed(() => this.computeInitials());
  readonly avatarColor = computed(() => this.computeColor(this.initials()));

  // region event handling methods
  // endregion

  // region protected, private helper methods
  private computeInitials(): string {
    const user = this.currentUser();
    if (!user) return '';
    const first = user.firstName?.trim();
    const last = user.lastName?.trim();
    if (first || last) return ((first?.[0] ?? '') + (last?.[0] ?? '')).toUpperCase();
    return (user.email ?? '').replace(/[^a-zA-Z0-9]/g, '').slice(0, 2).toUpperCase();
  }

  private computeColor(seed: string): string {
    let hash = 0;
    for (let i = 0; i < seed.length; i++) hash = seed.charCodeAt(i) + ((hash << 5) - hash);
    const hue = Math.abs(hash) % 360;
    return `hsl(${hue}, 55%, 45%)`;
  }
  // endregion
}
