import { Component, inject } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { Router } from '@angular/router';
import { NgOptimizedImage } from '@angular/common';

@Component({
  selector: 'app-header',
  imports: [MatToolbar, MatIcon, MatIconButton, NgOptimizedImage],
  template: `
    <mat-toolbar color="primary" class="app-header">
      <img ngSrc="assets/processpuzzle-logo-with-title.jpg" class="app-logo" priority width="75" height="85" (click)="navigateToHome()" alt="ProcessPuzzle Logo" />
      <button mat-icon-button class="example-icon" aria-label="Example icon-button with menu icon">
        <mat-icon>menu</mat-icon>
      </button>
      <span class="toolbar-spacer"></span>
      <button mat-icon-button class="example-icon favorite-icon" aria-label="Example icon-button with heart icon">
        <mat-icon>favorite</mat-icon>
      </button>
      <button mat-icon-button class="example-icon" aria-label="Example icon-button with share icon">
        <mat-icon>share</mat-icon>
      </button>
    </mat-toolbar>
  `,
  styleUrl: 'header.component.scss',
})
export class HeaderComponent {
  router = inject(Router);

  async navigateToHome() {
    await this.router.navigateByUrl('/');
  }
}
