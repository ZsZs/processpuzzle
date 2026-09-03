import { Component, inject, output } from '@angular/core';
import { NgClass, NgOptimizedImage } from '@angular/common';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatListItemIcon, MatListItemTitle } from '@angular/material/list';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatToolbar } from '@angular/material/toolbar';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AuthButtonComponent } from '@processpuzzle/auth';
import { LanguageSelectorComponent } from '@processpuzzle/base-widget';
import { LayoutService, NavigateBackComponent } from '@processpuzzle/util';
import { navigationItems } from '../navigation-routes';

/**
 * The application bar: logo, sidenav toggle, language selector and the sign-in button.
 *
 * On a handset the routes move into a menu, which is why they are read here as well as in the
 * sidenav: the sidenav renders nothing at that width.
 */
@Component({
  selector: 'app-header',
  imports: [
    AuthButtonComponent,
    LanguageSelectorComponent,
    MatButton,
    MatIcon,
    MatIconButton,
    MatListItemIcon,
    MatListItemTitle,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    MatToolbar,
    NavigateBackComponent,
    NgClass,
    NgOptimizedImage,
    RouterLink,
    TranslocoDirective,
  ],
  templateUrl: 'header.component.html',
  styleUrl: 'header.component.scss',
})
export class HeaderComponent {
  readonly layoutService = inject(LayoutService);
  readonly router = inject(Router);
  readonly routes = navigationItems(this.router);
  readonly title = 'ProcessPuzzle';
  readonly toggleSideNav = output<undefined>();

  async onLogoClick() {
    await this.router.navigateByUrl('/');
  }

  async onLogoKeyPress(event: KeyboardEvent) {
    if (event.key === 'Enter' || event.key === ' ' || event.code === 'Space') {
      event.preventDefault();
      await this.onLogoClick();
    }
  }

  sidenavToggle() {
    this.toggleSideNav.emit(undefined);
  }
}
