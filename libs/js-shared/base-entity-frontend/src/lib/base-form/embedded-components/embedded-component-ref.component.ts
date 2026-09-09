import { Component, input, InputSignal, output } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

/**
 * One row of {@link EmbeddedComponentsListComponent}. Presentation only: opening and deleting both belong to
 * the list, which is the side that knows where the row sits in the containing document.
 */
@Component({
  selector: 'app-embedded-component-ref',
  standalone: true,
  imports: [MatIconButton, MatIcon],
  template: `
    <a href="" (click)="requestOpen($event)">{{ displayName() }}</a>
    @if (!disabled()) {
      <button type="button" mat-icon-button class="base-entity-form-delete-button" aria-label="Delete embedded component" (click)="requestDelete()">
        <mat-icon>delete</mat-icon>
      </button>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
  styles: [
    `
      :host {
        display: flex;
        align-items: center;
        gap: 8px;
        width: 100%;
      }
      :host .base-entity-form-delete-button {
        margin-left: auto;
      }
    `,
  ],
})
export class EmbeddedComponentRefComponent {
  displayName: InputSignal<string> = input.required<string>();
  disabled: InputSignal<boolean> = input(false);
  readonly openRequested = output<void>();
  readonly deleteRequested = output<void>();

  requestOpen(event: Event): void {
    event.preventDefault();
    this.openRequested.emit();
  }

  requestDelete(): void {
    if (this.disabled()) return;
    this.deleteRequested.emit();
  }
}
