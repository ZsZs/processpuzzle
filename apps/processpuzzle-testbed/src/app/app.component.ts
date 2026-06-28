import { Component, effect, inject, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HeaderComponent } from './navigation/header/header.component';
import { SidenavComponent } from './navigation/sidenav/sidenav.component';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { FooterComponent } from './navigation/footer/footer.component';
import { MatIconRegistry } from '@angular/material/icon';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { DesignRouteService } from '@processpuzzle/design';

@Component({
  imports: [RouterModule, HeaderComponent, SidenavComponent, MatSidenav, MatSidenavContent, MatSidenavContainer, FooterComponent],
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly designRouteService = inject(DesignRouteService);
  readonly sidenavOpened = signal(!this.designRouteService.isDesignRoute());
  title = 'processpuzzle-testbed';

  constructor(iconRegistry: MatIconRegistry) {
    iconRegistry.setDefaultFontSetClass('material-symbols-outlined');
    effect(() => this.sidenavOpened.set(!this.designRouteService.isDesignRoute()));
  }

  toggleSidenav() {
    this.sidenavOpened.update((opened) => !opened);
  }
}
