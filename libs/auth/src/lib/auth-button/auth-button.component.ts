import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { AuthService } from '../domain/auth.service';

@Component({
  selector: 'pp-auth-button',
  template: `
    <div class="auth-button">
      <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Auth Button">
        <mat-icon>person</mat-icon>
      </button>
      <mat-menu #menu="matMenu">
        <ng-container *ngIf="!isAuthenticated()">
          <button mat-menu-item routerLink="/auth/login">
            <mat-icon>login</mat-icon>
            <span>Login</span>
          </button>

          <button mat-menu-item routerLink="/auth/register">
            <mat-icon>person_add</mat-icon>
            <span>Register</span>
          </button>
        </ng-container>

        <ng-container *ngIf="isAuthenticated()">
          <button mat-menu-item routerLink="/auth/personal-data">
            <mat-icon>person</mat-icon>
            <span>Personal Data</span>
          </button>

          <button mat-menu-item routerLink="/auth/logout">
            <mat-icon>logout</mat-icon>
            <span>Logout</span>
          </button>
        </ng-container>
      </mat-menu>
    </div>
  `,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatMenu, MatMenuItem, RouterLink, MatMenuTrigger],
  styleUrl: './auth-button.component.css',
})
export class AuthButtonComponent {
  private readonly authService = inject(AuthService);
  isAuthenticated = computed(() => this.authService.isAuthenticated());

  // region event handling methods
  // endregion
}
