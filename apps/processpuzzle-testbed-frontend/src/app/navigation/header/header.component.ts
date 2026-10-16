import { Component, inject, output } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { NgClass } from '@angular/common';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { appRoutes } from '../../app.routes';
import { MatListItemIcon, MatListItemTitle } from '@angular/material/list';
import { TranslocoDirective } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AuthButtonComponent } from '@processpuzzle/auth';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { DesignRouteService } from '@processpuzzle/design';
import { LayoutService, NavigateBackComponent, SubstringPipe } from '@processpuzzle/util';
// eslint-disable-next-line @nx/enforce-module-boundaries
import {
  AppLogoComponent,
  AppTitleComponent,
  DesignButtonComponent,
  LanguageSelectorComponent,
  LikeButtonComponent,
  provideAppPropertyStore,
  ShareButtonComponent,
  TaskDashboardButtonComponent,
} from '@processpuzzle/widgets';

@Component({
  selector: 'app-header',
  imports: [
    AuthButtonComponent,
    AppLogoComponent,
    AppTitleComponent,
    MatToolbar,
    MatIcon,
    MatIconButton,
    MatButton,
    MatMenu,
    MatMenuTrigger,
    RouterLink,
    NgClass,
    MatMenuItem,
    MatListItemIcon,
    MatListItemTitle,
    NavigateBackComponent,
    DesignButtonComponent,
    TaskDashboardButtonComponent,
    LikeButtonComponent,
    ShareButtonComponent,
    LanguageSelectorComponent,
    TranslocoDirective,
    SubstringPipe,
  ],
  templateUrl: 'header.component.html',
  styleUrl: 'header.component.scss',
  providers: [provideAppPropertyStore()],
})
export class HeaderComponent {
  readonly layoutService = inject(LayoutService);
  readonly routes = appRoutes.filter((item) => item.title !== null && item.title !== undefined);
  readonly toggleSideNav = output<undefined>();
  readonly isDesignRoute = inject(DesignRouteService).isDesignRoute;

  // region event handlers
  sidenavToggle() {
    this.toggleSideNav.emit(undefined);
  }
  // endregion
}
