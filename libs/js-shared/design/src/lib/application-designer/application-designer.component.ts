import { Component } from '@angular/core';
import { MatTabLink, MatTabNav, MatTabNavPanel } from '@angular/material/tabs';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { APPLICATION_DESIGNER_TABS } from './application-designer.tabs';

/**
 * The Application section of the designer: one page whose tabs switch between the three entities that
 * describe an application — its definition, the modules it mounts and the widget types those place.
 *
 * A `mat-tab-nav-bar` rather than a `mat-tab-group`, because every tab is a route: each one deep-links,
 * survives a reload and keeps the browser's Back button meaningful. Being the component of the *parent*
 * route, it also stays on screen while the user drills into a definition's form and its embedded levels,
 * and `routerLinkActive`'s default subset matching keeps the right tab lit down there.
 *
 * The `design` scope the labels are keys of is named **inline on the directive** rather than provided by the
 * component or its route, unlike the library's leaf components. Two reasons: the component then translates
 * wherever it is mounted, without the host having to register the scope; and the token is `multi: true`, so a
 * declaration that reaches further than one directive replaces rather than extends the collection it lands
 * beside — worth avoiding in a component whose whole job is to host somebody else's screens.
 */
@Component({
  selector: 'pp-application-designer',
  standalone: true,
  imports: [MatTabNav, MatTabNavPanel, MatTabLink, RouterLink, RouterLinkActive, RouterOutlet, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; scope: 'design'">
      <nav mat-tab-nav-bar [tabPanel]="tabPanel">
        @for (tab of tabs; track tab.path) {
          <a mat-tab-link [routerLink]="tab.path" routerLinkActive #linkActive="routerLinkActive" [active]="linkActive.isActive">
            <span class="material-symbols-outlined tab-icon">{{ tab.icon }}</span>
            {{ t(tab.label) }}
          </a>
        }
      </nav>
      <mat-tab-nav-panel #tabPanel>
        <router-outlet></router-outlet>
      </mat-tab-nav-panel>
    </ng-container>
  `,
  styles: `
    .tab-icon {
      margin-right: 6px;
    }
  `,
})
export class ApplicationDesignerComponent {
  readonly tabs = APPLICATION_DESIGNER_TABS;
}
