import { Component, input, InputSignal } from '@angular/core';
import { BaseEntity } from '../../base-entity/base-entity';

@Component({
  selector: 'app-entity-component-ref',
  standalone: true,
  template: `
    <a href="" (click)="navigateToRelated($event)">{{ componentName() }}</a>
  `,
  styles: ``,
})
export class EntityComponentRefComponent<ComponentEntity extends BaseEntity> {
  component: InputSignal<ComponentEntity> = input.required<ComponentEntity>();
  componentNameAttr: InputSignal<string> = input.required<string>();
  linkedEntityType: InputSignal<string> = input.required<string>();
  store: InputSignal<any> = input.required<any>();

  componentName(): string {
    const attrName = this.componentNameAttr();
    const component = this.component();
    const componentName = attrName ? (component as Record<string, unknown>)[attrName] : undefined;

    return componentName === undefined || componentName === null ? component.id : String(componentName);
  }

  navigateToRelated(event: Event): void {
    event.preventDefault();
    this.store().navigateToRelated(this.linkedEntityType(), this.component().id, this.store().determineCurrentUrl());
  }
}
