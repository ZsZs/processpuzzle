import { Component, computed, DestroyRef, inject } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { WorkingContextCrumbsComponent } from './working-context-crumbs.component';
import { WorkingContextService } from './working-context.service';

/**
 * The status bar of an application shell (its `statusbar` region): the active working contexts, on every page,
 * whether or not an entity form is open. Shown only while a context is active — an empty strip would be clutter.
 *
 * While it is on screen the status bar inside the entity tabs leaves the contexts to it, so they are not shown
 * twice; see {@link WorkingContextService.statusbarHostedByShell}.
 */
@Component({
  selector: 'pp-working-context-statusbar',
  standalone: true,
  imports: [MatToolbar, WorkingContextCrumbsComponent],
  template: `
    @if (isVisible()) {
      <mat-toolbar class="working-context-statusbar" data-testid="working-context-statusbar">
        <pp-working-context-crumbs />
      </mat-toolbar>
    }
  `,
  styles: [
    `
      .working-context-statusbar {
        background-color: #ffffff;
        border-radius: 6px;
      }
    `,
  ],
})
export class WorkingContextStatusbarComponent {
  private readonly workingContext = inject(WorkingContextService);
  protected readonly isVisible = computed(() => this.workingContext.active().length > 0);

  constructor() {
    this.workingContext.statusbarHostedByShell.set(true);
    inject(DestroyRef).onDestroy(() => this.workingContext.statusbarHostedByShell.set(false));
  }
}
