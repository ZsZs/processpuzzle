import { Component, inject, output } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NgClass, NgOptimizedImage } from '@angular/common';
import { LayoutService } from '@processpuzzle/util';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';

@Component({
  selector: 'app-header',
  imports: [MatToolbar, MatIcon, MatIconButton, NgOptimizedImage, MatButton, MatMenu, MatMenuTrigger, RouterLinkActive, RouterLink, NgClass, MatMenuItem],
  templateUrl: 'header.component.html',
  styleUrl: 'header.component.scss',
})
export class HeaderComponent {
  readonly layoutService = inject(LayoutService);
  readonly router = inject(Router);
  readonly toggleSideNav = output<undefined>();

  // region event handlers
  async navigateToHome() {
    await this.router.navigateByUrl('/');
  }

  sidenavToggle() {
    this.toggleSideNav.emit(undefined);
  }

  // endregion
}
