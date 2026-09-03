import { Component, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MatIconRegistry } from '@angular/material/icon';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { FooterComponent } from './navigation/footer/footer.component';
import { HeaderComponent } from './navigation/header/header.component';
import { SidenavComponent } from './navigation/sidenav/sidenav.component';

/**
 * Header, collapsible sidenav, routed content, footer — the testbed's shell without its design-mode
 * branch, which belongs to `@processpuzzle/design` and has no meaning here.
 */
@Component({
  selector: 'app-root',
  imports: [RouterModule, HeaderComponent, SidenavComponent, FooterComponent, MatSidenav, MatSidenavContainer, MatSidenavContent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  readonly sidenavOpened = signal(true);

  constructor(iconRegistry: MatIconRegistry) {
    // Every `<mat-icon>` in the application and in the framework libraries names a Material Symbol,
    // not a ligature of the older font. Without this the icons render as their own names.
    iconRegistry.setDefaultFontSetClass('material-symbols-outlined');
  }

  toggleSidenav() {
    this.sidenavOpened.update((opened) => !opened);
  }
}
