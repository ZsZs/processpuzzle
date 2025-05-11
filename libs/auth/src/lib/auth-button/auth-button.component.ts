import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { AuthService } from '../domain/auth.service';
import { authRoutes } from '../auth.routes';
import { SubstringPipe } from '@processpuzzle/util';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'pp-auth-button',
  template: `
    <div class="auth-button">
      <ng-container *transloco="let t">
        <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Auth Button">
          <mat-icon>person</mat-icon>
        </button>
        <mat-menu #menu="matMenu">
          @for (item of routes; track item) {
            <ng-container *ngIf="(isAuthenticated() && !item.data?.['authToggle']) || (!isAuthenticated() && item.data?.['authToggle'])">
              <button mat-menu-item [routerLink]="'auth/' + item.path">
                <mat-icon>{{ item.data?.['icon'] }}</mat-icon>
                <span>&nbsp;{{ t('auth.' + item.title | substring: 0) }}</span>
              </button>
            </ng-container>
          }
        </mat-menu>
      </ng-container>
    </div>
  `,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatMenu, MatMenuItem, RouterLink, MatMenuTrigger, SubstringPipe, TranslocoDirective],
  styles: [],
  providers: [provideTranslocoScope('auth')],
})
export class AuthButtonComponent {
  private readonly authService = inject(AuthService);
  isAuthenticated = computed(() => this.authService.isAuthenticated());
  readonly routes = authRoutes.filter((item) => item.title !== null && item.title !== undefined);

  // region event handling methods
  // endregion
}
