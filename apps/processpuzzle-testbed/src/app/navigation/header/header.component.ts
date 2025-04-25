import { Component, inject, output } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { NgClass, NgOptimizedImage } from '@angular/common';
import { AuthButtonComponent } from '@processpuzzle/auth';
import { LayoutService, SubstringPipe } from '@processpuzzle/util';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { appRoutes } from '../../app.routes';
import { MatListItemIcon, MatListItemTitle } from '@angular/material/list';
import { LanguageSelectorComponent, LikeButtonComponent, NavigateBackComponent, provideAppPropertyStore, ShareButtonComponent, WidgetsModule } from '@processpuzzle/widgets';

@Component({
  selector: 'app-header',
  imports: [
    AuthButtonComponent,
    MatToolbar,
    MatIcon,
    MatIconButton,
    NgOptimizedImage,
    MatButton,
    MatMenu,
    MatMenuTrigger,
    RouterLink,
    NgClass,
    MatMenuItem,
    MatListItemIcon,
    MatListItemTitle,
    SubstringPipe,
    NavigateBackComponent,
    LikeButtonComponent,
    ShareButtonComponent,
    LanguageSelectorComponent,
    WidgetsModule,
    AuthButtonComponent,
  ],
  templateUrl: 'header.component.html',
  styleUrl: 'header.component.scss',
  providers: [provideAppPropertyStore()],
})
export class HeaderComponent {
  readonly layoutService = inject(LayoutService);
  readonly router = inject(Router);
  readonly routes = appRoutes.filter((item) => item.title !== null && item.title !== undefined);
  readonly title = 'ProcessPuzzle Testbed';
  readonly toggleSideNav = output<undefined>();

  // region event handlers
  async onLogoClick() {
    await this.navigateToHome();
  }

  async onLogoKeyPress(event: KeyboardEvent) {
    if (event.key === 'Enter' || event.key === ' ' || event.code === 'Space') {
      event.preventDefault(); // Prevent default behavior (like scrolling for space)
      await this.onLogoClick();
    }
  }

  sidenavToggle() {
    this.toggleSideNav.emit(undefined);
  }

  // endregion

  // region protected, private helper methods
  private async navigateToHome() {
    await this.router.navigateByUrl('/');
  }

  // endregion
}
