import { Component, computed, inject, OnInit } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass, NgStyle } from '@angular/common';
import { ComponentNameAttr, EntityComponentRefComponent } from './entity-component-ref.component';
import { BaseEntityDescriptor } from '../../base-entity/base-entity.descriptor';
import { NGXLogger } from 'ngx-logging-kit';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { NavigatorCommand } from '../../base-form-navigator/navigation-payload';

@Component({
  selector: 'app-component-list',
  standalone: true,
  imports: [NgClass, NgStyle, EntityComponentRefComponent, MatButton, MatIcon],
  template: `
    @if (config().visible) {
      <div class="row">
        <fieldset class="base-entity-form-field" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().label }}</legend>
          <ul [id]="config().attrName" class="base-entity-form-list">
            @for (component of components(); track component.id) {
              <li>
                <app-entity-component-ref
                  [entity]="entity()"
                  [component]="component"
                  [componentNameAttr]="componentNameAttr()"
                  [formGroup]="formGroup"
                  [linkedEntityType]="linkedEntityType().entityName"
                />
              </li>
            }
          </ul>
          <button type="button" mat-button [title]="addComponentTitle()" [attr.aria-label]="addComponentTitle()" (click)="navigateToRelatedList()">
            <mat-icon>add</mat-icon>
            {{ addComponentTitle() }}
          </button>
        </fieldset>
      </div>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
})
export class EntityComponentsListComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  private readonly logger = inject(NGXLogger);

  linkedEntityType = computed<BaseEntityDescriptor>(() => {
    this.logger.debug('config(): ', this.config());
    const linkedEntityType = this.config().linkedEntityType;
    if (linkedEntityType === undefined) {
      throw new Error('linkedEntityType should be defined for EntityComponentsListComponent.');
    }

    return linkedEntityType;
  });

  ngOnInit(): void {
    this.addSelectedComponentFromNavigatorResponse();
  }

  componentNameAttr(): ComponentNameAttr {
    return {
      attrName: this.linkedEntityType().componentIdentification(),
      name: this.config().attrName,
    };
  }

  components(): BaseEntity[] {
    const value = this.formGroup.get(this.config().attrName)?.value ?? this.value();
    return Array.isArray(value) ? value : [];
  }

  addComponentTitle(): string {
    return 'Add ' + this.linkedEntityType().entityName;
  }

  navigateToRelatedList(): void {
    this.formNavigator.navigateToRelatedList(this.linkedEntityType().entityName, this.formNavigator.determineCurrentUrl(), {
      command: NavigatorCommand.SELECT_OR_CREATE,
    });
  }

  private addSelectedComponentFromNavigatorResponse(): void {
    const responsePayload = this.formNavigator.popResponsePayload(NavigatorCommand.SELECT_OR_CREATE);
    if (!responsePayload?.payload) {
      return;
    }

    const components = [...this.components(), responsePayload.payload as BaseEntity];
    const attrName = this.config().attrName;
    const entity = this.entity() as Record<string, unknown>;
    entity[attrName] = components;

    const control = this.formGroup.get(attrName);
    control?.setValue(components);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
