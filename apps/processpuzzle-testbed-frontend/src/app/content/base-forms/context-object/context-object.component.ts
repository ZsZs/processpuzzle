import { Component, computed, DestroyRef, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { TranslocoDirective } from '@jsverse/transloco';
import { MarkdownComponent } from 'ngx-markdown';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { WorkingContextService, WorkingContextStatusbarComponent } from '@processpuzzle/base-entity';
import { AGGREGATE_ROOT_PATH, COMPONENT_ONE_PATH, COMPONENT_TWO_PATH, CONTEXT_OBJECT_BASE_PATH, DEMO_CONTEXT_NAME, DEMO_ROOT_NAME } from './context-object.routes';

/**
 * The Context Object demonstration: pick an aggregate root once, then edit its parts without opening it again.
 *
 * It declares the working context (the Demo Root, from base-entity) for as long as the page is open, shows the selected
 * root in a status bar, and offers three buttons: the root's own list, where it is selected, and one list for each kind of
 * part. The part buttons lead into the selected root; with none selected they lead to a page that asks for one.
 */
@Component({
  selector: 'base-entity-context-object',
  standalone: true,
  imports: [MarkdownComponent, MatButtonToggle, MatButtonToggleGroup, MatDivider, RouterLink, RouterOutlet, TranslocoDirective, WorkingContextStatusbarComponent],
  template: `
    <ng-container *transloco="let t; prefix: 'base-entity'">
      <markdown clipboard [src]="explanationSource"></markdown>
      <pp-working-context-statusbar />
      @if (!hasContext()) {
        <p class="context-object-hint" data-testid="context-object-no-context">{{ t('context_object_no_context') }}</p>
      }
      <div class="context-object-buttons">
        <mat-button-toggle-group name="contextObject" aria-label="Context Object">
          <mat-button-toggle [routerLink]="aggregateRootLink" [value]="aggregateRootPath" data-testid="context-object-aggregate-root">Aggregate Root</mat-button-toggle>
          <mat-button-toggle [routerLink]="componentLink(componentOnePath)" [value]="componentOnePath" data-testid="context-object-component-one">Embedded Component One</mat-button-toggle>
          <mat-button-toggle [routerLink]="componentLink(componentTwoPath)" [value]="componentTwoPath" data-testid="context-object-component-two">Embedded Component Two</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      <mat-divider />
      <router-outlet></router-outlet>
    </ng-container>
  `,
  styles: `
    .context-object-buttons {
      margin: 16px 0;
    }
    .context-object-hint {
      opacity: 0.8;
    }
  `,
})
export class ContextObjectComponent {
  readonly explanationSource = 'assets/base-forms/context-object.md';
  readonly aggregateRootPath = AGGREGATE_ROOT_PATH;
  readonly componentOnePath = COMPONENT_ONE_PATH;
  readonly componentTwoPath = COMPONENT_TWO_PATH;
  readonly aggregateRootLink = [AGGREGATE_ROOT_PATH];

  private readonly workingContext = inject(WorkingContextService);
  /** Whether the URL names a selected root. Off the context pages it does not, though the last selection is remembered. */
  protected readonly hasContext = computed(() => this.workingContext.current(DEMO_CONTEXT_NAME) !== undefined);

  constructor() {
    // The context exists while this page does: the testbed's application declares none of its own.
    const unregister = this.workingContext.register([
      {
        name: DEMO_CONTEXT_NAME,
        entityName: DEMO_ROOT_NAME,
        basePath: CONTEXT_OBJECT_BASE_PATH,
        picker: { searchFields: ['name', 'description'], recent: 3 },
        display: { label: '{{name}}', extra: '{{description}}' },
      },
    ]);
    inject(DestroyRef).onDestroy(unregister);
  }

  /** Into the selected root when the URL names one, otherwise to the page that asks for one (and restores the last). */
  protected componentLink(part: string): string[] {
    const active = this.workingContext.current(DEMO_CONTEXT_NAME);
    return active ? ['ctx', DEMO_CONTEXT_NAME, active.id, part] : ['ctx', DEMO_CONTEXT_NAME, part];
  }
}
