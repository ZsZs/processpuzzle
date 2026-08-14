import { Component } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { APPLICATION_DESIGNER_TABS } from './application-designer.tabs';

/**
 * The Application section of the designer: one page whose tabs switch between the three entities that
 * describe an application — its definition, the modules it mounts and the widget types those place.
 *
 * This uses route buttons instead of a second tab bar so the page does not stack two nearly identical tab
 * controls on top of each other. Each button still deep-links, survives a reload and keeps the browser's
 * Back button meaningful. Being the component of the *parent* route, it also stays on screen while the
 * user drills into a definition's form and its embedded levels, and `routerLinkActive`'s default subset
 * matching keeps the right view highlighted down there.
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
  imports: [MatButton, RouterLink, RouterLinkActive, RouterOutlet, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; scope: 'design'">
      <nav class="designer-nav" aria-label="Application designer views">
        @for (tab of tabs; track tab.path) {
          <a
            mat-stroked-button
            class="designer-nav-button"
            [routerLink]="tab.path"
            routerLinkActive="designer-nav-button-active"
            ariaCurrentWhenActive="page"
          >
            <span class="material-symbols-outlined tab-icon">{{ tab.icon }}</span>
            {{ t(tab.label) }}
          </a>
        }
      </nav>
      <router-outlet></router-outlet>
    </ng-container>
  `,
  styles: `
    .designer-nav {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 16px;
    }

    .designer-nav-button {
      border-radius: 999px;
    }

    .designer-nav-button-active {
      background-color: rgb(0 0 0 / 4%);
      border-color: currentcolor;
    }

    .tab-icon {
      margin-right: 6px;
    }
  `,
})
export class ApplicationDesignerComponent {
  readonly tabs = APPLICATION_DESIGNER_TABS;
}
