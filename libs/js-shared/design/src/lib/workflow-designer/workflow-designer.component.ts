import { Component } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { WORKFLOW_DESIGNER_TABS } from './workflow-designer.tabs';

/**
 * The Workflows section of the designer: one page whose tabs switch between the six entities of
 * base-workflow — the workflow itself, the four catalog definitions it composes, and the instances it
 * produces.
 *
 * Route buttons instead of a second tab bar, so the page does not stack two nearly identical tab
 * controls on top of each other. Each button still deep-links, survives a reload and keeps the browser's
 * Back button meaningful. Being the component of the *parent* route, it also stays on screen while the
 * user drills into a definition's form and its embedded levels — a workflow's task assignments and
 * `*Use` rows, a task's steps — and `routerLinkActive`'s default subset matching keeps the right view
 * highlighted down there.
 *
 * The `design` scope the labels are keys of is named **inline on the directive** rather than provided by
 * the component or its route, for the two reasons `ApplicationDesignerComponent` gives: the component
 * then translates wherever it is mounted, without the host having to register the scope; and the token
 * is `multi: true`, so a declaration reaching further than one directive would replace rather than
 * extend the collection it lands beside — worth avoiding in a component whose whole job is to host
 * somebody else's screens. Those screens bring their own `base_workflow` and `base_entity` scopes,
 * declared on each branch of `BASE_WORKFLOW_ROUTES`.
 *
 * A component of its own rather than a generalization of `ApplicationDesignerComponent`: the two are
 * the same shape today, but the Application section is shipped and tested, and this section is the
 * place the graphical workflow modeler will land — at which point the two stop being the same shape.
 */
@Component({
  selector: 'pp-workflow-designer',
  standalone: true,
  imports: [MatButton, RouterLink, RouterLinkActive, RouterOutlet, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; scope: 'design'">
      <nav class="designer-nav" aria-label="Workflow designer views">
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
export class WorkflowDesignerComponent {
  readonly tabs = WORKFLOW_DESIGNER_TABS;
}
