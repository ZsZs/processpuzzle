import { Component, inject, output } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { NgClass, NgOptimizedImage } from '@angular/common';
import { LayoutService } from '@processpuzzle/util';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { appRoutes } from '../app.routes';
import { MatListItemIcon, MatListItemTitle } from '@angular/material/list';
import { SubstringPipe } from '../substring-pipe';

@Component({
  selector: 'app-header',
  imports: [MatToolbar, MatIcon, MatIconButton, NgOptimizedImage, MatButton, MatMenu, MatMenuTrigger, RouterLink, NgClass, MatMenuItem, MatListItemIcon, MatListItemTitle, SubstringPipe],
  templateUrl: 'header.component.html',
  styleUrl: 'header.component.scss',
})
export class HeaderComponent {
  readonly layoutService = inject(LayoutService);
  readonly router = inject(Router);
  readonly routes = appRoutes.filter((item) => item.title !== null && item.title !== undefined);
  readonly title = 'ProcessPuzzle Testbed';
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
