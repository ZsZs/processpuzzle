import { Component, computed, inject, input } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { createTestId } from '../base-entity/base-entity-utility';
import { WorkingContextService } from './working-context.service';

/**
 * The transitions the **active** entity of a working context can take — Start race, Finish race — as buttons. Placed on
 * a page that declares them, so the officer acts on the race without reaching for the status bar's menu.
 *
 * It shows nothing without an active context, and nothing for an entity no state machine governs. `triggers` narrows the
 * offer to the named triggers, in that order; without it every transition the entity can take is offered. A transition a
 * guard refuses is shown disabled, with the reason as its tooltip.
 */
@Component({
  selector: 'pp-working-context-actions',
  standalone: true,
  imports: [MatButton],
  template: `
    @for (transition of transitions(); track transition.key) {
      <button
        type="button"
        mat-flat-button
        [attr.data-testid]="testId(transition.key)"
        [disabled]="!transition.enabled || firing()"
        [title]="transition.blockedReason ?? ''"
        (click)="onFire(transition.key)"
      >
        {{ transition.label }}
      </button>
    }
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        flex-wrap: wrap;
        gap: 8px;
      }
      button {
        min-height: 48px;
      }
    `,
  ],
})
export class WorkingContextActionsComponent {
  /** The context whose active entity the buttons act on. */
  readonly context = input.required<string>();
  /** The triggers to offer, in order; every available transition when absent. */
  readonly triggers = input<string[] | undefined>(undefined);

  private readonly workingContext = inject(WorkingContextService);
  protected readonly firing = this.workingContext.firing;

  protected readonly transitions = computed(() => {
    const available = this.workingContext.stateOf(this.context())?.transitions ?? [];
    const wanted = this.triggers();
    if (!wanted?.length) return available;
    return wanted.flatMap((trigger) => available.filter((transition) => transition.key === trigger));
  });

  protected async onFire(triggerKey: string): Promise<void> {
    await this.workingContext.fire(this.context(), triggerKey);
  }

  protected testId(triggerKey: string): string {
    return createTestId(this.workingContext.definitionOf(this.context())?.entityName ?? this.context(), 'context-action-' + triggerKey);
  }
}
