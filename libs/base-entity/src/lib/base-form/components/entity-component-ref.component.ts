import { Component, inject, input, InputSignal } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { BaseEntity, PersistedEntity } from '../../base-entity/base-entity';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';

export interface ComponentNameAttr {
  attrName: string;
  name: string;
}

@Component({
  selector: 'app-entity-component-ref',
  standalone: true,
  imports: [MatIconButton, MatIcon],
  template: `
    <a href="" (click)="navigateToRelated($event)">{{ componentName() }}</a>
    @if (!disabled()) {
      <button type="button" mat-icon-button class="base-entity-form-delete-button" aria-label="Delete component reference" (click)="removeComponent()">
        <mat-icon>cancel</mat-icon>
      </button>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
})
export class EntityComponentRefComponent<Entity extends BaseEntity, ComponentEntity extends BaseEntity> {
  entity: InputSignal<Entity> = input.required<Entity>();
  component: InputSignal<PersistedEntity<ComponentEntity>> = input.required<PersistedEntity<ComponentEntity>>();
  componentNameAttr: InputSignal<ComponentNameAttr> = input.required<ComponentNameAttr>();
  disabled: InputSignal<boolean> = input(false);
  formGroup: InputSignal<FormGroup> = input.required<FormGroup>();
  linkedEntityType: InputSignal<string> = input.required<string>();
  private readonly formNavigator = inject(BaseFormNavigatorSingletonStore);

  componentName(): string {
    const attrName = this.componentNameAttr().attrName;
    const component = this.component();
    const componentName = attrName ? (component as unknown as Record<string, unknown>)[attrName] : undefined;

    return componentName === undefined || componentName === null ? component.id : String(componentName);
  }

  navigateToRelated(event: Event): void {
    event.preventDefault();
    this.formNavigator.navigateToRelated(this.linkedEntityType(), this.component().id, this.formNavigator.determineCurrentUrl());
  }

  removeComponent(): void {
    if (this.disabled()) {
      return;
    }

    const componentsAttrName = this.componentNameAttr().name;
    const entity = this.entity() as Record<string, unknown>;
    const components = entity[componentsAttrName];

    if (!Array.isArray(components)) {
      return;
    }

    const remainingComponents = components.filter((component) => (component as BaseEntity).id !== this.component().id);
    entity[componentsAttrName] = remainingComponents;

    const control = this.formGroup().get(componentsAttrName);
    control?.setValue(remainingComponents);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
