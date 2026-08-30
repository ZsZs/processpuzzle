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
 * Three toggles because base-workflow has three routable aggregates: the process a tenant authors, the
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
          <mat-button-toggle routerLink="process-definition" value="process-definition">Process Definition</mat-button-toggle>
          <mat-button-toggle routerLink="tool-definition" value="tool-definition">Tool Definition</mat-button-toggle>
          <mat-button-toggle routerLink="process-instance" value="process-instance">Process Instance</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      <mat-divider />
      <router-outlet></router-outlet>
    </ng-container>
  `,
})
export class SamplesComponent implements OnInit {
  router = inject(Router);
  selectedButton: WritableSignal<string> = signal('process-definition');

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
          // `process-instance` is tested before `process-definition`: both contain the substring
          // `process-`, but only one of them contains the other's full segment — neither does, so the
          // order is not load-bearing here, unlike a `tool` / `tool-definition` pair would be. Kept
          // explicit anyway so adding a fourth aggregate cannot break it silently.
          if (currentUrl.includes('process-instance')) this.selectedButton.set('process-instance');
          else if (currentUrl.includes('tool-definition')) this.selectedButton.set('tool-definition');
          else if (currentUrl.includes('process-definition')) this.selectedButton.set('process-definition');
          else this.selectedButton.set('');
        }
      });
  }
}
