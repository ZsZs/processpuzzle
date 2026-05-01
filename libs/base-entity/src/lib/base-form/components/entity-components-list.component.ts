import { Component, computed, inject } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass, NgStyle } from '@angular/common';
import { EntityComponentRefComponent } from './entity-component-ref.component';
import { BaseEntityDescriptor } from '../../base-entity/base-entity.descriptor';
import { NGXLogger } from 'ngx-logging-kit';

@Component({
  selector: 'app-component-list',
  standalone: true,
  imports: [NgClass, NgStyle, EntityComponentRefComponent],
  template: `
    @if (config().visible) {
      <div class="row">
        <fieldset class="base-entity-form-field" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().label }}</legend>
          <ul [id]="config().attrName" class="base-entity-form-list">
            @for (component of components(); track component.id) {
              <li>
                <app-entity-component-ref [component]="component" [componentNameAttr]="componentNameAttr()" [linkedEntityType]="linkedEntityType().entityName" [store]="store" />
              </li>
            }
          </ul>
        </fieldset>
      </div>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
})
export class EntityComponentsListComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  private readonly logger = inject(NGXLogger);

  linkedEntityType = computed<BaseEntityDescriptor>(() => {
    this.logger.debug('config(): ', this.config());
    const linkedEntityType = this.config().linkedEntityType;
    if (linkedEntityType === undefined) {
      throw new Error('linkedEntityType should be defined for EntityComponentsListComponent.');
    }

    return linkedEntityType;
  });

  componentNameAttr(): string {
    return this.linkedEntityType().componentIdentification();
  }

  components(): BaseEntity[] {
    const value = this.formGroup.get(this.config().attrName)?.value ?? this.value();
    return Array.isArray(value) ? value : [];
  }
}
