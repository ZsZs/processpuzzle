import { Component, input, InputSignal, output } from '@angular/core';
import { CdkDragHandle } from '@angular/cdk/drag-drop';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * One row of {@link EmbeddedComponentsListComponent}. Presentation only: opening, deleting and moving all
 * belong to the list, which is the side that knows where the row sits in the containing document.
 *
 * An `ordered` row carries a drag handle — the list is the drop target — and move up / down buttons, which
 * are what a keyboard or screen-reader user reorders with, since dragging needs a pointer.
 */
@Component({
  selector: 'app-embedded-component-ref',
  standalone: true,
  imports: [CdkDragHandle, MatIconButton, MatIcon, TranslocoPipe],
  template: `
    @if (ordered() && !disabled()) {
      <mat-icon cdkDragHandle class="base-entity-form-drag-handle" data-testid="embedded-row-drag-handle">drag_indicator</mat-icon>
    }
    <a href="" (click)="requestOpen($event)">{{ displayName() }}</a>
    @if (!disabled()) {
      <span class="base-entity-form-row-actions">
        @if (ordered()) {
          <button
            type="button"
            mat-icon-button
            class="base-entity-form-row-button"
            data-testid="embedded-row-move-up"
            [disabled]="first()"
            [attr.aria-label]="'base_entity.embedded_components.move_up' | transloco"
            [title]="'base_entity.embedded_components.move_up' | transloco"
            (click)="moveUpRequested.emit()"
          >
            <mat-icon>arrow_upward</mat-icon>
          </button>
          <button
            type="button"
            mat-icon-button
            class="base-entity-form-row-button"
            data-testid="embedded-row-move-down"
            [disabled]="last()"
            [attr.aria-label]="'base_entity.embedded_components.move_down' | transloco"
            [title]="'base_entity.embedded_components.move_down' | transloco"
            (click)="moveDownRequested.emit()"
          >
            <mat-icon>arrow_downward</mat-icon>
          </button>
        }
        <button type="button" mat-icon-button class="base-entity-form-delete-button" aria-label="Delete embedded component" (click)="requestDelete()">
          <mat-icon>delete</mat-icon>
        </button>
      </span>
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
      :host .base-entity-form-row-actions {
        align-items: center;
        display: flex;
        margin-left: auto;
      }
      :host .base-entity-form-drag-handle {
        cursor: grab;
        opacity: 0.6;
      }
    `,
  ],
})
export class EmbeddedComponentRefComponent {
  displayName: InputSignal<string> = input.required<string>();
  disabled: InputSignal<boolean> = input(false);
  /** Offers the drag handle and the move buttons. */
  ordered: InputSignal<boolean> = input(false);
  first: InputSignal<boolean> = input(false);
  last: InputSignal<boolean> = input(false);
  readonly openRequested = output<void>();
  readonly deleteRequested = output<void>();
  readonly moveUpRequested = output<void>();
  readonly moveDownRequested = output<void>();

  requestOpen(event: Event): void {
    event.preventDefault();
    this.openRequested.emit();
  }

  requestDelete(): void {
    if (this.disabled()) return;
    this.deleteRequested.emit();
  }
}
