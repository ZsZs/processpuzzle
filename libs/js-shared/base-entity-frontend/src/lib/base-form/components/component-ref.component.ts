import { Component, inject, input, InputSignal, output } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { BaseEntity, PersistedEntity } from '../../base-entity/base-entity';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';

/**
 * One row of {@link ComponentsListComponent}. Presentation only: the delete button asks the list to destroy
 * the component, because deleting a part reaches the child's store and needs a confirmation the row has no
 * business owning.
 */
@Component({
  selector: 'app-component-ref',
  standalone: true,
  imports: [MatIconButton, MatIcon],
  template: `
    <a href="" (click)="navigateToComponent($event)">{{ displayName() }}</a>
    @if (!disabled()) {
      <button type="button" mat-icon-button class="base-entity-form-delete-button" aria-label="Delete component" (click)="requestDelete()">
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
export class ComponentRefComponent<ComponentEntity extends BaseEntity> {
  componentEntity: InputSignal<PersistedEntity<ComponentEntity>> = input.required<PersistedEntity<ComponentEntity>>();
  displayName: InputSignal<string> = input.required<string>();
  disabled: InputSignal<boolean> = input(false);
  linkedEntityType: InputSignal<string> = input.required<string>();
  readonly deleteRequested = output<void>();

  private readonly formNavigator = inject(BaseFormNavigatorSingletonStore);

  navigateToComponent(event: Event): void {
    event.preventDefault();
    this.formNavigator.navigateToRelated(this.linkedEntityType(), this.componentEntity().id, this.formNavigator.determineCurrentUrl());
  }

  requestDelete(): void {
    if (this.disabled()) return;
    this.deleteRequested.emit();
  }
}
