import { Component, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { filter, startWith } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';

/**
 * Host of the `base-workflow` samples. The screens themselves come from `BASE_WORKFLOW_ROUTES`, mounted as
 * this route's children in `app.routes.ts` — the same arrangement the `base-state` and `base-app` samples
 * use, so what is demonstrated here is the library's own branches rather than a copy of them.
 *
 * Three toggles because base-workflow has three routable aggregates: the workflow a tenant authors, the
 * tools its steps call, and the runs it produces. The last is read-only, which the screens themselves make
 * plain — every field is disabled and Save is dead.
 */
@Component({
  selector: 'base-workflows-samples',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, MatButtonToggleGroup, MatButtonToggle, MatDivider, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-workflows'">
      <div style="margin-bottom: 20px">{{ t('samples_desc_1') }}</div>
      <div>
        <strong>{{ t('samples_desc_2') }}</strong>
      </div>
      <div style="margin-top: 20px">
        <mat-button-toggle-group name="workflowSample" [value]="selectedButton()" aria-label="Workflow Sample">
          <mat-button-toggle routerLink="workflow" value="workflow">Workflow</mat-button-toggle>
          <mat-button-toggle routerLink="tool-definition" value="tool-definition">Tool Definition</mat-button-toggle>
          <mat-button-toggle routerLink="workflow-instance" value="workflow-instance">Workflow Instance</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      <mat-divider />
      <router-outlet></router-outlet>
    </ng-container>
  `,
})
export class SamplesComponent implements OnInit {
  router = inject(Router);
  selectedButton: WritableSignal<string> = signal('workflow');

  ngOnInit() {
    this.subscribeToRoutingEvents();
  }

  private subscribeToRoutingEvents() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        startWith(this.router),
      )
      .subscribe((event) => {
        const currentUrl: string = event.url;
        if (currentUrl) {
          // Order is load-bearing here, and each segment is matched with its leading slash. Both
          // matter since the aggregate was renamed from `process-definition` to `workflow`:
          // `workflow-instance` now begins with `workflow`, so the instance has to be tested first;
          // and the parent route is `base-workflows`, which contains `workflow` as a bare substring,
          // so `/workflow` is what tells the child apart from the parent that hosts it.
          if (currentUrl.includes('/workflow-instance')) this.selectedButton.set('workflow-instance');
          else if (currentUrl.includes('/tool-definition')) this.selectedButton.set('tool-definition');
          else if (currentUrl.includes('/workflow')) this.selectedButton.set('workflow');
          else this.selectedButton.set('');
        }
      });
  }
}
